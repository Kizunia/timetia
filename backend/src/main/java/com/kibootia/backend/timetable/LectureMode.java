package com.kibootia.backend.timetable;

/**
 * How a lecture is held (data model {@code lecture_mode}). An in-person lecture has a room and an online one has none, but
 * may carry a meeting link.
 */
public enum LectureMode {

	IN_PERSON, ONLINE

}
