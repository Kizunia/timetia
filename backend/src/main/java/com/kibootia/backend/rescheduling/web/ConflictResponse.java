package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.timetable.ConflictType;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** One thing in the way of a ticket. Every conflict blocks; nobody can override one. */
public record ConflictResponse(
		@Schema(requiredMode = REQUIRED) ConflictType type,
		@Schema(requiredMode = REQUIRED) String message,
		@Schema(nullable = true, description = "The lecture the ticket changes.") UUID lectureId,
		@Schema(nullable = true, description = "The lecture that holds what the ticket needs.") UUID conflictingLectureId,
		@Schema(nullable = true, description = "The ticket that put that lecture there, when one did.") UUID conflictingTicketId) {

}
