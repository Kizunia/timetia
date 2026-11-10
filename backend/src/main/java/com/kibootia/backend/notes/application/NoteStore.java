package com.kibootia.backend.notes.application;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

/**
 * Where notes are kept. The use cases depend on this port, never on JPA (ADR 0003). Reads and writes act on the
 * current tenant's live notes; only {@link #restore} and {@link #purgeDeletedBefore} see deleted ones.
 */
public interface NoteStore {

	Note insert(UUID ownerId, String title, String body);

	Optional<Note> find(UUID id);

	/**
	 * Changes a note if it is still at {@code expectedVersion}; otherwise fails with an optimistic locking failure.
	 */
	Note update(UUID id, long expectedVersion, String title, String body);

	/** Marks the note deleted. It stays in the table until the retention purge. */
	void softDelete(UUID id);

	/**
	 * Brings a deleted note back, recorded as changed by {@code actor}. Returns false when there is no deleted note with
	 * that ID.
	 */
	boolean restore(UUID id, UUID actor);

	/**
	 * @param owner only this owner's notes, or all when null
	 * @param text only notes whose title or body contains this text, ignoring case, or all when null
	 */
	Page<Note> search(UUID owner, String text, Pageable pageable);

	/** Whether the owner has a live note with this title, other than {@code except}. */
	boolean titleInUse(UUID owner, String title, UUID except);

	/** Permanently removes notes deleted before the cutoff and returns how many. */
	int purgeDeletedBefore(Instant cutoff);

	/** Permanently removes every note the user owns, deleted or not, when the user is erased. Returns how many. */
	int purgeOwnedBy(UUID owner);

}
