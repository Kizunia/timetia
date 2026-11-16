package com.kibootia.backend.rescheduling.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.timetable.LectureMode;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** Where a lecture is or goes: the date, the hours and how it is held. */
public record TargetResponse(
		@Schema(requiredMode = REQUIRED) LocalDate date,
		@Schema(requiredMode = REQUIRED) LocalTime startTime,
		@Schema(requiredMode = REQUIRED) LocalTime endTime,
		@Schema(requiredMode = REQUIRED) LectureMode mode,
		@Schema(nullable = true) UUID roomId,
		@Schema(nullable = true, description = "The room's code; absent when it is online or the room was deleted.") String roomCode,
		@Schema(nullable = true) String meetingLink) {

}
