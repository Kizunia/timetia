package com.kibootia.backend.rescheduling.web;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.timetable.LectureMode;

/**
 * Where a reschedule puts the lecture: the date, the hours (on the hour, the same length as the lecture), and how it is
 * held. An in-person lecture needs {@code roomId}; an online one must not have it and may carry a {@code meetingLink}. The
 * server checks all of it and reports each bad field.
 */
public record TargetRequest(@NotNull LocalDate date, @NotNull LocalTime startTime, @NotNull LocalTime endTime,
		@NotNull LectureMode mode, UUID roomId, @Size(max = 500) String meetingLink) {

}
