package com.kibootia.backend.notes.application;

import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.notes.NoteDeleted;
import com.kibootia.backend.notes.NotesPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.authorization.Scope;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.events.EventContextFactory;

/**
 * The use cases of the reference feature. Each shows a convention of the foundation:
 *
 * <ul>
 * <li>a permission-only check with {@code @RequiresPermission} ({@link #create}, {@link #restore});</li>
 * <li>a check against the loaded record with {@code authorization.require} ({@link #update}, {@link #delete});</li>
 * <li>a list limited by {@link Scope} ({@link #list});</li>
 * <li>a record the caller may not see answers 404, so its existence is not revealed (ADR 0043);</li>
 * <li>an auditable change writes its audit entry, and an event that must not be lost is published, both in the
 * transaction of the change (ADR 0046, ADR 0031).</li>
 * </ul>
 */
@Service
public class NoteService {

	public static final String NOT_FOUND = "notes.not_found";

	public static final String DUPLICATE_TITLE = "notes.duplicate_title";

	private final NoteStore notes;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	NoteService(NoteStore notes, Authorization authorization, AuditLog auditLog, ApplicationEventPublisher events,
			EventContextFactory eventContexts) {
		this.notes = notes;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.events = events;
		this.eventContexts = eventContexts;
	}

	@Transactional
	@RequiresPermission(NotesPermissions.CREATE)
	public Note create(NewNote note) {
		UUID owner = this.authorization.currentUserId();
		String title = note.title().strip();
		if (this.notes.titleInUse(owner, title, null)) {
			throw duplicateTitle();
		}
		return this.notes.insert(owner, title, note.body());
	}

	@Transactional(readOnly = true)
	public Note get(UUID id) {
		Note note = this.notes.find(id).orElseThrow(NoteService::notFound);
		if (!this.authorization.can(NotesPermissions.VIEW, note)) {
			throw notFound();
		}
		return note;
	}

	@Transactional
	public Note update(UUID id, NoteChanges changes) {
		Note current = visible(id);
		this.authorization.require(NotesPermissions.UPDATE, current);
		String title = changes.title().strip();
		if (!title.equals(current.title()) && this.notes.titleInUse(current.ownerId(), title, id)) {
			throw duplicateTitle();
		}
		return this.notes.update(id, changes.expectedVersion(), title, changes.body());
	}

	@Transactional
	public void delete(UUID id) {
		Note note = visible(id);
		this.authorization.require(NotesPermissions.DELETE, note);
		this.notes.softDelete(id);
		this.auditLog.record(AuditEntry.of("notes.note.deleted", "note", id));
		this.events.publishEvent(new NoteDeleted(id, this.eventContexts.capture()));
	}

	/**
	 * A page of notes: all of them for a caller who may view any, otherwise only their own. With {@code deleted}, the
	 * deleted notes of everyone, which only someone who may restore notes can see; they are listed so that one can be
	 * found and restored.
	 */
	@Transactional(readOnly = true)
	public Page<Note> list(NoteFilter filter, Pageable pageable) {
		if (filter.deleted()) {
			this.authorization.require(NotesPermissions.RESTORE);
			return this.notes.searchDeleted(filter.owner(), filter.text(), pageable);
		}
		Scope scope = this.authorization.requireScope(NotesPermissions.VIEW);
		UUID me = this.authorization.currentUserId();
		if (scope == Scope.OWN) {
			if (filter.owner() != null && !filter.owner().equals(me)) {
				return Page.empty(pageable);
			}
			return this.notes.search(me, filter.text(), pageable);
		}
		return this.notes.search(filter.owner(), filter.text(), pageable);
	}

	@Transactional
	@RequiresPermission(NotesPermissions.RESTORE)
	public Note restore(UUID id) {
		if (!this.notes.restore(id, this.authorization.currentUserId())) {
			throw ApiException.notFound(NOT_FOUND, "There is no deleted note with that ID.");
		}
		this.auditLog.record(AuditEntry.of("notes.note.restored", "note", id));
		return this.notes.find(id).orElseThrow();
	}

	/**
	 * Loads a note the caller may see. A note that does not exist and a note the caller may not see look the same: 404.
	 */
	private Note visible(UUID id) {
		return this.notes.find(id)
			.filter((note) -> this.authorization.can(NotesPermissions.VIEW, note))
			.orElseThrow(NoteService::notFound);
	}

	private static ApiException notFound() {
		return ApiException.notFound(NOT_FOUND, "There is no note with that ID.");
	}

	private static ApiException duplicateTitle() {
		return ApiException.conflict(DUPLICATE_TITLE, "You already have a note with this title.");
	}

}
