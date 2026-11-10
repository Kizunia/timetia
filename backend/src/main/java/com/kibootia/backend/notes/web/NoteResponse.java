package com.kibootia.backend.notes.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A note as the API returns it. The {@code version} is what a later update must send back; the tenant is not shown,
 * because the caller only ever sees their own.
 */
public record NoteResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) UUID ownerId,
		@Schema(requiredMode = REQUIRED) String title,
		@Schema(requiredMode = REQUIRED) String body,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt,
		@Schema(nullable = true, description = "Changes whenever the picture does; null when there is none. Use it to refresh a cached image.") UUID attachmentVersion) {

}
