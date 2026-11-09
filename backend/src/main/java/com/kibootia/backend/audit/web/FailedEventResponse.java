package com.kibootia.backend.audit.web;

import java.time.Instant;
import java.util.UUID;

/**
 * An event whose delivery failed, as the API shows it. Metadata only: the payload can hold personal data and is never
 * returned.
 */
public record FailedEventResponse(UUID id, String eventType, String listener, Instant publishedAt, int attempts,
		Instant lastAttemptAt, boolean retriesExhausted) {

}
