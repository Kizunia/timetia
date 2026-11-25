package com.kibootia.backend.rescheduling.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/tickets/{id}}: the plan of a ticket that is not yet applied, replaced as a whole. Editing a
 * rejected ticket makes it a draft again, one revision later. {@code reviewers}, for a ticket under review, replaces the
 * people asked; leave it out to keep them. {@code version} is the version of the ticket the client last read: if it changed
 * since, the edit is refused with 409 instead of overwriting the other change.
 */
public record UpdateTicketRequest(@Size(max = 1000) String description, @Valid TargetRequest target,
		@Size(max = 500) String reason, UUID substituteFacultyId, @Size(max = 20) List<@Valid ReviewerBody> reviewers,
		@NotNull @PositiveOrZero Long version) {

}
