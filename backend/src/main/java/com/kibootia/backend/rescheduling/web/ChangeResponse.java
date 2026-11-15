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
		@Schema(requiredMode = REQUIRED) UUID lectureId,
		@Schema(nullable = true) TargetResponse target,
		@Schema(nullable = true) String reason,
		@Schema(nullable = true) TargetResponse before) {

}
