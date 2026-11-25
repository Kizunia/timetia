package com.kibootia.backend.rescheduling.web;

import java.time.LocalDate;
import java.time.LocalTime;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.timetable.LectureMode;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A lecture as it is now, in the words a ticket page needs: when, where, which subject and who teaches it. */
public record LectureSummaryResponse(
		@Schema(requiredMode = REQUIRED) LocalDate date,
		@Schema(requiredMode = REQUIRED) LocalTime startTime,
		@Schema(requiredMode = REQUIRED) LocalTime endTime,
		@Schema(requiredMode = REQUIRED) LectureMode mode,
		@Schema(nullable = true) String roomCode,
		@Schema(nullable = true) String subjectCode,
		@Schema(nullable = true) String facultyName) {

}
