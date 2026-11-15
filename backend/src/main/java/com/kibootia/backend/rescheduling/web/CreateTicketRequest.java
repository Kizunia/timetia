package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.rescheduling.domain.TicketType;

/**
 * The body of {@code POST /api/v1/tickets}: a draft ticket for one lecture. A {@code RESCHEDULE} says where the lecture goes
 * ({@code target}); a {@code CANCEL} gives a {@code reason} and no target.
 */
public record CreateTicketRequest(@NotNull TicketType type, @NotNull UUID lectureId, @Size(max = 1000) String description,
		@Valid TargetRequest target, @Size(max = 500) String reason) {

}
