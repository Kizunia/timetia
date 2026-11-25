package com.kibootia.backend.timetable.web;

import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.timetable.LectureMode;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** One weekly lecture of a version as the API returns it. */
public record TimetableEntryResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) UUID versionId,
		@Schema(requiredMode = REQUIRED) UUID assignmentId,
		@Schema(requiredMode = REQUIRED, description = "1 (Monday) to 6 (Saturday).") int dayOfWeek,
		@Schema(requiredMode = REQUIRED) LocalTime startTime,
		@Schema(requiredMode = REQUIRED) LocalTime endTime,
		@Schema(nullable = true) UUID roomId,
		@Schema(requiredMode = REQUIRED) LectureMode mode,
		@Schema(nullable = true) String meetingLink) {

}
