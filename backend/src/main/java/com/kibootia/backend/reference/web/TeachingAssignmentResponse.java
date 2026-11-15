package com.kibootia.backend.reference.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.academic.SessionType;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A teaching assignment as the API returns it. {@code batchId} is null for a theory assignment. */
public record TeachingAssignmentResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) UUID divisionId,
		@Schema(nullable = true) UUID batchId,
		@Schema(requiredMode = REQUIRED) UUID subjectId,
		@Schema(requiredMode = REQUIRED) UUID facultyId,
		@Schema(requiredMode = REQUIRED) SessionType sessionType,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
