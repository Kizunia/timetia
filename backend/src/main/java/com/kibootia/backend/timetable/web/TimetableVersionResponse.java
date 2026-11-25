package com.kibootia.backend.timetable.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.timetable.application.VersionStatus;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A timetable version as the API returns it. */
public record TimetableVersionResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) UUID divisionId,
		@Schema(requiredMode = REQUIRED) UUID termId,
		@Schema(requiredMode = REQUIRED) LocalDate effectiveFrom,
		@Schema(requiredMode = REQUIRED) VersionStatus status,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
