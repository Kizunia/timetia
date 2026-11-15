package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.rescheduling.domain.TicketType;

/**
 * The body of {@code POST /api/v1/tickets}: a draft ticket for one lecture. A {@code RESCHEDULE} says where the lecture goes
 * ({@code target}); a {@code CANCEL} gives a {@code reason} and no target; a {@code SUBSTITUTE} names the faculty member who
 * takes the lecture ({@code substituteFacultyId}) and no target; an {@code EXTRA} names the teaching assignment
 * ({@code assignmentId}), not a lecture, and the slot ({@code target}). An {@code EXCHANGE} names the author's lecture
 * ({@code lectureId}) and the one to exchange places with ({@code otherLectureId}) and no target. Every other type names the
 * lecture ({@code lectureId}).
 */
public record CreateTicketRequest(@NotNull TicketType type, UUID lectureId, UUID otherLectureId, UUID assignmentId, @Size(max = 1000) String description,
		@Valid TargetRequest target, @Size(max = 500) String reason, UUID substituteFacultyId) {

}
