package com.kibootia.backend.timetable.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;

/**
 * Works out what a lecture holds. A lecture holds, for each hour it lasts: its faculty member; its room, if it is held in
 * person; and its students. A theory lecture is for the whole division, so it holds the division and every batch in it; a lab
 * or tutorial is for one batch, so it holds only that. Two batches of a division can therefore have labs at the same time,
 * while a theory lecture and a lab of one of its batches cannot overlap.
 *
 * <p>Hours are whole hours: a lecture from 09:00 to 11:00 holds the hours 9 and 10. Online lectures hold no room.
 */
public final class Claims {

	private Claims() {
	}

	/**
	 * @param roomId the room, or null for an online lecture
	 * @param studentBatchIds every batch the lecture is for: all the batches of the division for a theory lecture, the one
	 *            batch for a lab or tutorial
	 */
	public static Set<ClaimKey> keysFor(SessionType sessionType, UUID facultyId, UUID roomId, UUID divisionId,
			Set<UUID> studentBatchIds, LocalDate date, LocalTime start, LocalTime end) {
		Set<ClaimKey> keys = new LinkedHashSet<>();
		for (int hour = start.getHour(); hour < end.getHour(); hour++) {
			keys.add(new ClaimKey(ClaimKind.FACULTY, facultyId, date, hour));
			if (roomId != null) {
				keys.add(new ClaimKey(ClaimKind.ROOM, roomId, date, hour));
			}
			if (sessionType == SessionType.THEORY) {
				keys.add(new ClaimKey(ClaimKind.DIVISION, divisionId, date, hour));
			}
			for (UUID batchId : studentBatchIds) {
				keys.add(new ClaimKey(ClaimKind.STUDENT_BATCH, batchId, date, hour));
			}
		}
		return keys;
	}

}
