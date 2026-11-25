package com.kibootia.backend.notes;

import java.util.UUID;

import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * A note that had a picture was removed for good by the retention purge, and its file must go too. Published in the
 * transaction of the purge and handled through a {@code @DurableEventListener}, so a storage server that is down for a
 * while delays the removal of the file and never undoes the purge or loses the file for good (ADR 0031).
 *
 * <p>Notes without a picture publish nothing: there is nothing to remove for them.
 */
public record NotePurged(UUID noteId, UUID attachmentFileId, EventContext context) implements DomainEvent {

}
