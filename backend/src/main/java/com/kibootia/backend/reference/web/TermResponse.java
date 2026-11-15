package com.kibootia.backend.reference.web;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** An academic term as the API returns it. */
public record TermResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String code,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) LocalDate startDate,
		@Schema(requiredMode = REQUIRED) LocalDate endDate,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
