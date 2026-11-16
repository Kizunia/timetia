package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.rescheduling.domain.ChangeOperation;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * What a ticket does to a lecture. {@code target} is where a move puts it and is absent for a cancellation;
 * {@code before} is where it was, and is present once the ticket is applied.
 */
public record ChangeResponse(
		@Schema(requiredMode = REQUIRED) ChangeOperation operation,
		@Schema(nullable = true, description = "The lecture the ticket changes; for an extra lecture, the one it made, once applied.") UUID lectureId,
		@Schema(nullable = true, description = "The teaching assignment an extra lecture is for.") UUID assignmentId,
		@Schema(nullable = true) TargetResponse target,
		@Schema(nullable = true) String reason,
		@Schema(nullable = true, description = "Who takes the lecture; present for a substitute.") UUID substituteFacultyId,
		@Schema(nullable = true) String substituteFacultyName,
		@Schema(nullable = true) TargetResponse before,
		@Schema(nullable = true, description = "Who taught the lecture before the ticket was applied.") UUID beforeFacultyId,
		@Schema(nullable = true) String beforeFacultyName,
		@Schema(nullable = true, description = "The lecture as it is now. Only in the detail of one ticket, never in a list.") LectureSummaryResponse lecture) {

}
