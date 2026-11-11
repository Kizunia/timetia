package com.kibootia.backend.notes.application;

import java.time.Instant;
import java.util.List;
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

	/**
	 * The current tenant's deleted notes, sorted as asked and filtered like {@link #search}. Only an administrative view
	 * may call this; the notes are not live and cannot be read through {@link #find}.
	 */
	Page<Note> searchDeleted(UUID owner, String text, Pageable pageable);

	/** Whether the owner has a live note with this title, other than {@code except}. */
	boolean titleInUse(UUID owner, String title, UUID except);

	/**
	 * Makes {@code fileId} the picture of a live note, or removes it when {@code fileId} is null, and says which file it
	 * replaced. Does not change the note's version. Empty when there is no live note with that ID.
	 */
	Optional<AttachmentChange> replaceAttachment(UUID noteId, UUID fileId);

	/** The file a note had before its attachment was replaced; null when it had none. */
	record AttachmentChange(UUID previousFileId) {

	}

	/** The notes that {@link #purgeDeletedBefore} would remove and that have a file, so the files can be removed too. */
	List<AttachedFile> attachmentsDeletedBefore(Instant cutoff);

	/** A note and the stored file attached to it. */
	record AttachedFile(UUID noteId, UUID fileId) {

	}

	/** Permanently removes notes deleted before the cutoff and returns how many. */
	int purgeDeletedBefore(Instant cutoff);

	/** Permanently removes every note the user owns, deleted or not, when the user is erased. Returns how many. */
	int purgeOwnedBy(UUID owner);

}
