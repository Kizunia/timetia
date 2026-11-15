package com.kibootia.backend.rescheduling.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A ticket as the API returns it. {@code version} is what an edit must send back. {@code conflicts} is what the latest check
 * found, and is empty in a list; {@code conflicted} says whether it found anything. {@code actingFacultyId} is the faculty
 * member an HOD acts for.
 */
public record TicketResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) TicketType type,
		@Schema(requiredMode = REQUIRED) TicketStatus status,
		@Schema(nullable = true) ApprovalPath approvalPath,
		@Schema(requiredMode = REQUIRED) UUID authorUserId,
		@Schema(nullable = true) UUID actingFacultyId,
		@Schema(nullable = true) String description,
		@Schema(requiredMode = REQUIRED) int revisionNo,
		@Schema(requiredMode = REQUIRED) UUID divisionId,
		@Schema(nullable = true) UUID batchId,
		@Schema(requiredMode = REQUIRED) SessionType sessionType,
		@Schema(requiredMode = REQUIRED) UUID facultyId,
		@Schema(requiredMode = REQUIRED) ChangeResponse change,
		@Schema(requiredMode = REQUIRED) List<ConflictResponse> conflicts,
		@Schema(requiredMode = REQUIRED) boolean conflicted,
		@Schema(requiredMode = REQUIRED, description = "When the earliest lecture it involves starts; a ticket not yet applied lapses then.") Instant expiresAt,
		@Schema(nullable = true) Instant closedAt,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
