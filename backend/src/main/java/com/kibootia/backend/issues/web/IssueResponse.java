package com.kibootia.backend.issues.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.issues.domain.IssueStatus;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * An issue as the API returns it. {@code batchId} is null for a theory issue, which is for the whole division.
 * {@code mentionedFacultyIds} are faculty records; {@code linkedTicketIds} are the tickets that answer it.
 */
public record IssueResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) IssueStatus status,
		@Schema(requiredMode = REQUIRED) SessionType sessionType,
		@Schema(requiredMode = REQUIRED) UUID departmentId,
		@Schema(requiredMode = REQUIRED) UUID divisionId,
		@Schema(nullable = true) UUID batchId,
		@Schema(requiredMode = REQUIRED) String title,
		@Schema(nullable = true) String description,
		@Schema(requiredMode = REQUIRED) UUID openerUserId,
		@Schema(requiredMode = REQUIRED) List<UUID> mentionedFacultyIds,
		@Schema(requiredMode = REQUIRED) List<UUID> linkedTicketIds,
		@Schema(nullable = true) Instant closedAt,
		@Schema(nullable = true) UUID closedByUserId,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
