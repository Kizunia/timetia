package com.kibootia.backend.rescheduling.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A person the author of a ticket may ask to review it, with the capacity to send with the request. */
public record ReviewerCandidateResponse(
		@Schema(requiredMode = REQUIRED) UUID userId,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED, description = "The capacity to name when asking this person: HOD, CR, BR or AFFECTED_FACULTY.") ReviewerCapacity capacity) {

}
