package com.kibootia.backend.reference.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A faculty member as the API returns it. {@code userId} is null when the person has no account yet. */
public record FacultyResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String code,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) UUID departmentId,
		@Schema(nullable = true) UUID userId,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
