package com.kibootia.backend.timetable.web;

import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.timetable.LectureMode;

/**
 * The body of {@code POST /api/v1/timetable/versions/{id}/entries}: one weekly lecture of a draft. {@code dayOfWeek} is 1
 * (Monday) to 6 (Saturday); times are on the hour between 08:00 and 20:00. An in-person lecture needs {@code roomId}; an
 * online one must not have it and may carry a {@code meetingLink}. The server checks all of it and reports each bad field.
 */
public record CreateEntryRequest(@NotNull UUID assignmentId, @NotNull Integer dayOfWeek, @NotNull LocalTime startTime,
		@NotNull LocalTime endTime, UUID roomId, @NotNull LectureMode mode, @Size(max = 500) String meetingLink) {

}
