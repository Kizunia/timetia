package com.kibootia.backend.notes.persistence;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;

import com.kibootia.backend.notes.application.Note;
import com.kibootia.backend.notes.application.NoteStore;
import com.kibootia.backend.platform.persistence.UuidBytes;
import com.kibootia.backend.platform.tenancy.TenantContext;

/** Keeps notes in MySQL through JPA. */
@Component
class JpaNoteStore implements NoteStore {

	private final NoteRepository notes;

	private final Clock clock;

	JpaNoteStore(NoteRepository notes, Clock clock) {
		this.notes = notes;
		this.clock = clock;
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

	@Override
	public boolean titleInUse(UUID owner, String title, UUID except) {
		return this.notes.titleInUse(owner, title, except);
	}

	@Override
	public int purgeDeletedBefore(Instant cutoff) {
		return this.notes.purgeDeletedBefore(UuidBytes.toBytes(TenantContext.require()), utc(cutoff));
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
