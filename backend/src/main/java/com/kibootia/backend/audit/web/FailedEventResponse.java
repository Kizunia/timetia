package com.kibootia.backend.audit.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * An event whose delivery failed, as the API shows it. Metadata only: the payload can hold personal data and is never
 * returned.
 */
public record FailedEventResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String eventType,
		@Schema(requiredMode = REQUIRED) String listener,
		@Schema(requiredMode = REQUIRED) Instant publishedAt,
		@Schema(requiredMode = REQUIRED) int attempts,
		@Schema(requiredMode = REQUIRED) Instant lastAttemptAt,
		@Schema(requiredMode = REQUIRED) boolean retriesExhausted) {

}
