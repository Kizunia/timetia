package com.kibootia.backend.notes.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import com.kibootia.backend.identity.UserErased;
import com.kibootia.backend.notes.NoteDeleted;
import com.kibootia.backend.notes.NotePurged;
import com.kibootia.backend.platform.events.DurableEventListener;
import com.kibootia.backend.platform.files.StoredFiles;

/**
 * The reference for reacting to an event that must not be lost. The methods are not public, so they are not use cases:
 * each is reached only through its event, as the system, in the event's tenant, once per event even if the delivery
 * repeats. A handler that fails is retried with a back-off until it succeeds, which is why removing a file from the
 * storage server belongs here and not in the transaction that decided to remove the note.
 */
@Component
class NoteEvents {

	private static final Logger log = LoggerFactory.getLogger(NoteEvents.class);

	private final NoteStore notes;

	private final ObjectProvider<StoredFiles> files;

	NoteEvents(NoteStore notes, ObjectProvider<StoredFiles> files) {
		this.notes = notes;
		this.files = files;
	}

	/** A note was deleted. It can still be restored, so its picture stays until {@link NotePurged}. */
	@DurableEventListener
	void on(NoteDeleted event) {
		log.info("Note {} was deleted; its picture is kept until the retention purge", event.noteId());
	}

	/** A note with a picture was purged: the file is hidden now and the files module removes it after its own retention. */
	@DurableEventListener
	void on(NotePurged event) {
		StoredFiles storage = this.files.getIfAvailable();
		if (storage == null) {
			log.info("Note {} was purged with files switched off; its file {} is left alone", event.noteId(),
					event.attachmentFileId());
			return;
		}
		storage.discard(event.attachmentFileId());
		log.info("Note {} was purged; its file {} was discarded", event.noteId(), event.attachmentFileId());
	}

	/**
	 * A user was erased (ADR 0034): the notes they wrote go with them, deleted ones included. Their pictures are files the
	 * user owns (see {@link NoteAttachmentService}), so the files module removes those on the same event.
	 */
	@DurableEventListener
	void on(UserErased event) {
		int removed = this.notes.purgeOwnedBy(event.userId());
		log.info("Removed {} notes of erased user {}", removed, event.userId());
	}

}
