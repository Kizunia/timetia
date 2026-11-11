package com.kibootia.backend.platform.events;

/**
 * An event that must not be lost (ADR 0031). Publish it with {@code ApplicationEventPublisher.publishEvent} in the same
 * transaction as the change; the registry keeps it until a {@link DurableEventListener} has handled it. The record must
 * serialise to JSON and keep the context in a component named {@code context}, because the failed-event view finds the
 * tenant there.
 *
 * <pre>{@code
 * record NoteDeleted(UUID noteId, EventContext context) implements DomainEvent { }
 * }</pre>
 */
public interface DomainEvent {

	EventContext context();

}
