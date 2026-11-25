package com.kibootia.backend.timetable;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Where and when one lecture is held: the date, the hours, and how. It describes a lecture as the base timetable has it,
 * as it is now after changes, and as a change would leave it.
 *
 * @param roomId the room of an in-person lecture; null for an online one
 * @param meetingLink the link of an online lecture, optional; null for an in-person one
 */
public record LectureSlot(LocalDate date, LocalTime startTime, LocalTime endTime, LectureMode mode, UUID roomId,
		String meetingLink) {

	/** How many whole hours the lecture lasts; a lecture starts and ends on the hour. */
	public int hours() {
		return this.endTime.getHour() - this.startTime.getHour();
	}

}
