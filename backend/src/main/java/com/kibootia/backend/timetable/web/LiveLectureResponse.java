package com.kibootia.backend.timetable.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.timetable.LectureMode;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A lecture on one date as the live timetable shows it, with any applied change laid over the base timetable. The names and
 * codes are those of the reference data as it is now, and are null if what they describe was deleted since the timetable was
 * published. {@code batchId} is null for a theory lecture, which is for the whole division. {@code roomId} is null for an
 * online lecture, which may have a meeting link.
 *
 * <p>A cancelled lecture is shown, marked {@code cancelled}, so nobody goes to it. A lecture that was moved is shown where it
 * is now, marked {@code changed}, with the time the base timetable had it in the {@code original…} fields. The ticket that
 * made the change is not shown: people see the result of a change, not the ticket.
 */
public record LiveLectureResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) LocalDate date,
		@Schema(requiredMode = REQUIRED) LocalTime startTime,
		@Schema(requiredMode = REQUIRED) LocalTime endTime,
		@Schema(requiredMode = REQUIRED) SessionType sessionType,
		@Schema(requiredMode = REQUIRED) LectureMode mode,
		@Schema(requiredMode = REQUIRED) UUID divisionId,
		@Schema(nullable = true) String divisionCode,
		@Schema(nullable = true) UUID batchId,
		@Schema(nullable = true) String batchCode,
		@Schema(nullable = true) String subjectCode,
		@Schema(nullable = true) String subjectName,
		@Schema(requiredMode = REQUIRED) UUID facultyId,
		@Schema(nullable = true) String facultyName,
		@Schema(nullable = true) UUID roomId,
		@Schema(nullable = true) String roomCode,
		@Schema(nullable = true) String meetingLink,
		@Schema(requiredMode = REQUIRED) boolean cancelled,
		@Schema(requiredMode = REQUIRED) boolean changed,
		@Schema(nullable = true, description = "The date in the base timetable; present only when the lecture was moved.") LocalDate originalDate,
		@Schema(nullable = true, description = "The start in the base timetable; present only when the lecture was moved.") LocalTime originalStartTime,
		@Schema(nullable = true, description = "The end in the base timetable; present only when the lecture was moved.") LocalTime originalEndTime) {

}
