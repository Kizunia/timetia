package com.kibootia.backend.notes.application;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.kibootia.backend.notes.NoteDeleted;
import com.kibootia.backend.platform.events.DurableEventListener;

/**
 * The reference for reacting to an event that must not be lost. Milestone 5 removes a note's attachments here. The
 * method is not public, so it is not a use case: it is reached only through the event, as the system, in the event's
 * tenant, once per event even if the delivery repeats.
 */
@Component
class NoteEvents {

	private static final Logger log = LoggerFactory.getLogger(NoteEvents.class);

	@DurableEventListener
	void on(NoteDeleted event) {
		log.info("Note {} was deleted; nothing is attached to it yet", event.noteId());
	}

}
