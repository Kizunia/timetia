package com.kibootia.backend.timetable.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A room that is free at the time asked, with what a screen shows next to its code. */
public record FreeRoomResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String code,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED, description = "CLASSROOM, TUTORIAL_ROOM or LAB.") String roomType,
		@Schema(nullable = true, description = "The number of seats, when it was recorded.") Integer capacity,
		@Schema(requiredMode = REQUIRED, description = "Whether the kind of room suits the session type asked for; true when none was asked.") boolean fits) {

}
