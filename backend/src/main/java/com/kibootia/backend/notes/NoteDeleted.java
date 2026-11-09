package com.kibootia.backend.notes;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * A note was deleted. Part of the module's public contract: other modules react to it (the files module will remove a
 * note's attachments in milestone 5) through a {@code @DurableEventListener}, so the reaction survives a restart.
 */
public record NoteDeleted(UUID noteId, EventContext context) implements DomainEvent {

}
