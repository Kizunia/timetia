package com.kibootia.backend.timetable.application;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

import com.kibootia.backend.timetable.LectureMode;

/**
 * One weekly lecture of a version: a teaching assignment placed on a day and time, in a room or online.
 *
 * @param dayOfWeek 1 for Monday to 6 for Saturday
 * @param roomId the room of an in-person lecture; null for an online one
 */
public record TimetableEntry(UUID id, UUID timetableVersionId, UUID assignmentId, int dayOfWeek, LocalTime startTime,
		LocalTime endTime, UUID roomId, LectureMode mode, String meetingLink, long version, Instant createdAt) {

}
