package com.kibootia.backend.reference.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.reference.application.RoomType;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A room as the API returns it. {@code capacity} is null when the number of seats is unknown. */
public record RoomResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String code,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) RoomType roomType,
		@Schema(nullable = true) Integer capacity,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
