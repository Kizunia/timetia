package com.kibootia.backend.platform.events;

import java.time.Instant;
import java.util.UUID;

/**
 * What travels inside every durable event (ADR 0039): the registry stores an event as serialised data, so the tenant
 * and the trace cannot ride on the thread that delivers it. Create one with {@link EventContextFactory#capture()}.
 *
 * @param eventId identifies the event; handlers use it to skip a repeated delivery
 * @param tenantId the tenant the event happened in; restored before the handler runs
 * @param traceparent the W3C trace context of the publisher, or null when it was not traced
 * @param occurredAt when the event was raised
 */
public record EventContext(UUID eventId, UUID tenantId, String traceparent, Instant occurredAt) {

}
