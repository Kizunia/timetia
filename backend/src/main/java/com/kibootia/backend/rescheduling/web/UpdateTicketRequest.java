package com.kibootia.backend.rescheduling.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/tickets/{id}}: the plan of a draft, replaced as a whole. {@code version} is the version of
 * the ticket the client last read: if it changed since, the edit is refused with 409 instead of overwriting the other change.
 */
public record UpdateTicketRequest(@Size(max = 1000) String description, @Valid TargetRequest target,
		@Size(max = 500) String reason, @NotNull @PositiveOrZero Long version) {

}
