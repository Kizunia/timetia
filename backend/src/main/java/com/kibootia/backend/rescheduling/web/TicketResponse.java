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
		@Schema(nullable = true, description = "The author's name; absent when the account is gone.") String authorName,
		@Schema(nullable = true) UUID actingFacultyId,
		@Schema(nullable = true) String description,
		@Schema(requiredMode = REQUIRED) int revisionNo,
		@Schema(requiredMode = REQUIRED) UUID divisionId,
		@Schema(nullable = true) UUID batchId,
		@Schema(requiredMode = REQUIRED) SessionType sessionType,
		@Schema(requiredMode = REQUIRED) UUID facultyId,
		@Schema(nullable = true, description = "Subject code of the lecture's teaching assignment; absent when it was deleted.") String subjectCode,
		@Schema(nullable = true) String subjectName,
		@Schema(nullable = true) String divisionCode,
		@Schema(nullable = true, description = "Batch code of a lab or tutorial; absent for theory.") String batchCode,
		@Schema(nullable = true, description = "Name of the teacher the lecture had when the ticket was raised.") String facultyName,
		@Schema(requiredMode = REQUIRED, description = "The first change: the only one of most tickets, and for an exchange the one about the author's own lecture.") ChangeResponse change,
		@Schema(requiredMode = REQUIRED, description = "Every change the ticket makes, in order: one, or two for an exchange.") List<ChangeResponse> changes,
		@Schema(requiredMode = REQUIRED) List<ConflictResponse> conflicts,
		@Schema(requiredMode = REQUIRED, description = "Who was asked to review it, of every revision, oldest first; empty in a list.") List<ReviewResponse> reviews,
		@Schema(requiredMode = REQUIRED) boolean conflicted,
		@Schema(requiredMode = REQUIRED, description = "When the earliest lecture it involves starts; a ticket not yet applied lapses then.") Instant expiresAt,
		@Schema(nullable = true) Instant closedAt,
		@Schema(nullable = true, description = "The applied ticket this one undoes; present on a revert.") UUID revertsTicketId,
		@Schema(nullable = true, description = "The revert that undid this ticket; present once it is reverted.") UUID revertedByTicketId,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
