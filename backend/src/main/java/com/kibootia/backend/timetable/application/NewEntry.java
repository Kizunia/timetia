package com.kibootia.backend.timetable.application;

import java.time.LocalTime;
import java.util.UUID;

import com.kibootia.backend.timetable.LectureMode;

/** What is needed to add a lecture to a draft version. */
public record NewEntry(UUID assignmentId, int dayOfWeek, LocalTime startTime, LocalTime endTime, UUID roomId,
		LectureMode mode, String meetingLink) {

}
