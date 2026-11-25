package com.kibootia.backend.notes.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.notes.application.Note;
import com.kibootia.backend.notes.application.NoteStore;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/** Keeps notes in MySQL through JPA. */
@Component
class JpaNoteStore implements NoteStore {

	private final NoteRepository notes;

	private final Clock clock;

	private final EntityManager entityManager;

	JpaNoteStore(NoteRepository notes, Clock clock, EntityManager entityManager) {
		this.notes = notes;
		this.clock = clock;
		this.entityManager = entityManager;
	}

	@Override
	public Note insert(UUID ownerId, String title, String body) {
		// Flushed now so that a duplicate title is reported by this call, not at commit.
		return NoteEntityMapper.toModel(this.notes.saveAndFlush(NoteEntityMapper.toNewEntity(ownerId, title, body)));
	}

	@Override
	public Optional<Note> find(UUID id) {
		return this.notes.findById(id).map(NoteEntityMapper::toModel);
	}

	@Override
	public Note update(UUID id, long expectedVersion, String title, String body) {
		NoteEntity entity = this.notes.findById(id).orElseThrow();
		if (entity.getVersion() != expectedVersion) {
			throw new ObjectOptimisticLockingFailureException(NoteEntity.class, id);
		}
		entity.change(title, body);
		return NoteEntityMapper.toModel(this.notes.saveAndFlush(entity));
	}

	@Override
	public void softDelete(UUID id) {
		this.notes.findById(id).ifPresent(this.notes::delete);
		this.notes.flush();
	}

	@Override
	public boolean restore(UUID id, UUID actor) {
		return this.notes.restore(UuidBytes.toBytes(id), UuidBytes.toBytes(TenantContext.require()),
				utc(this.clock.instant()), UuidBytes.toBytes(actor)) == 1;
	}

	@Override
	public Page<Note> search(UUID owner, String text, Pageable pageable) {
		return this.notes.search(owner, likePattern(text), pageable).map(NoteEntityMapper::toModel);
	}

	/**
	 * Native SQL, because entity queries hide deleted rows, so the tenant is named here (ADR 0028) and so is the sort.
	 * Nothing the caller sends reaches the statement as text: the filters are bound parameters, and a sort field goes
	 * through a fixed list of columns.
	 */
	@Override
	public Page<Note> searchDeleted(UUID owner, String text, Pageable pageable) {
		StringBuilder where = new StringBuilder("tenant_id = :tenant and deleted_at is not null");
		if (owner != null) {
			where.append(" and owner_id = :owner");
		}
		String pattern = likePattern(text);
		if (pattern != null) {
			where.append(" and (lower(title) like :pattern escape '!' or lower(body) like :pattern escape '!')");
		}
		byte[] tenant = UuidBytes.toBytes(TenantContext.require());

		Query rows = this.entityManager
			.createNativeQuery("select * from note where " + where + " order by " + orderBy(pageable.getSort()), NoteEntity.class)
			.setFirstResult((int) pageable.getOffset())
			.setMaxResults(pageable.getPageSize());
		Query count = this.entityManager.createNativeQuery("select count(*) from note where " + where);
		for (Query query : new Query[] { rows, count }) {
			query.setParameter("tenant", tenant);
			if (owner != null) {
				query.setParameter("owner", UuidBytes.toBytes(owner));
			}
			if (pattern != null) {
				query.setParameter("pattern", pattern);
			}
		}

		@SuppressWarnings("unchecked")
		List<NoteEntity> found = rows.getResultList();
		long total = ((Number) count.getSingleResult()).longValue();
		return new PageImpl<>(found.stream().map(NoteEntityMapper::toModel).toList(), pageable, total);
	}

	/** The columns a deleted-notes list may be sorted by, keyed by the entity property the API's sort names map to. */
	private static String orderBy(Sort sort) {
		List<String> terms = new ArrayList<>();
		for (Sort.Order order : sort) {
			String column = switch (order.getProperty()) {
				case "title" -> "title";
				case "createdAt" -> "created_at";
				case "updatedAt" -> "updated_at";
				case "id" -> "id";
				default -> throw new IllegalArgumentException("Not a sortable field: " + order.getProperty());
			};
			terms.add(column + (order.isAscending() ? " asc" : " desc"));
		}
		return terms.isEmpty() ? "created_at desc, id desc" : String.join(", ", terms);
	}

	@Override
	@Transactional
	public Optional<AttachmentChange> replaceAttachment(UUID noteId, UUID fileId) {
		Optional<NoteEntity> locked = this.notes.lockById(noteId);
		if (locked.isEmpty()) {
			return Optional.empty();
		}
		UUID previous = locked.get().getAttachmentFileId();
		this.notes.setAttachment(UuidBytes.toBytes(noteId), UuidBytes.toBytes(TenantContext.require()),
				(fileId != null) ? UuidBytes.toBytes(fileId) : null);
		return Optional.of(new AttachmentChange(previous));
	}

	@Override
	public List<AttachedFile> attachmentsDeletedBefore(Instant cutoff) {
		return this.notes.attachmentsDeletedBefore(UuidBytes.toBytes(TenantContext.require()), utc(cutoff))
			.stream()
			.map((row) -> new AttachedFile(UuidBytes.fromBytes((byte[]) row[0]), UuidBytes.fromBytes((byte[]) row[1])))
			.toList();
	}

	@Override
	public boolean titleInUse(UUID owner, String title, UUID except) {
		return this.notes.titleInUse(owner, title, except);
	}

	@Override
	public int purgeDeletedBefore(Instant cutoff) {
		return this.notes.purgeDeletedBefore(UuidBytes.toBytes(TenantContext.require()), utc(cutoff));
	}

	@Override
	public int purgeOwnedBy(UUID owner) {
		return this.notes.purgeOwnedBy(UuidBytes.toBytes(TenantContext.require()), UuidBytes.toBytes(owner));
	}

	/** A lower-case LIKE pattern matching the text anywhere, with the user's own % and _ made literal. */
	private static String likePattern(String text) {
		if (text == null || text.isBlank()) {
			return null;
		}
		String escaped = text.strip().toLowerCase().replace("!", "!!").replace("%", "!%").replace("_", "!_");
		return "%" + escaped + "%";
	}

	/** DATETIME(6) columns hold UTC wall time, as Hibernate writes them. */
	private static LocalDateTime utc(Instant instant) {
		return LocalDateTime.ofInstant(instant, ZoneOffset.UTC);
	}

}
