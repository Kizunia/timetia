package com.kibootia.backend.notes;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * A note was deleted (it can still be restored until the retention purge). Part of the module's public contract: other
 * modules react to it through a {@code @DurableEventListener}, so the reaction survives a restart. The note's picture is
 * kept while the note can still be restored; {@link NotePurged} is the event that removes it.
 */
public record NoteDeleted(UUID noteId, EventContext context) implements DomainEvent {

}
