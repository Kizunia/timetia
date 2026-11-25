package com.kibootia.backend.timetable.domain;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Finds the double-bookings among the weekly entries of one draft version, so that a timetable being arranged is refused a
 * lecture that would book a teacher, a room or students twice. Two lectures clash when their times overlap and they need the
 * same faculty member, the same room, or the same students. Between divisions, and once lectures have dates, the same rule
 * is kept by the claims every dated lecture holds ({@link Claims}), which the database enforces.
 *
 * <p>Students clash when the lectures are of the same division and either is for the whole division (a theory lecture,
 * which has no batch) or both are for the same batch. Two different batches of one division may be taught at the same
 * time, which is how labs run in parallel. Lectures of different divisions never share students here.
 */
public final class Clashes {

	private Clashes() {
	}

	public enum Kind {

		FACULTY, ROOM, STUDENTS

	}

	/**
	 * A lecture that repeats weekly, as one entry of a version.
	 *
	 * @param batchId the batch of a lab or tutorial, or null for a lecture of the whole division
	 * @param roomId the room, or null for an online lecture
	 */
	public record Weekly(UUID id, int dayOfWeek, LocalTime start, LocalTime end, UUID facultyId, UUID roomId,
			UUID divisionId, UUID batchId) {

	}

	public record Clash<T>(T first, T second, Kind kind) {

	}

	/** Every pair of weekly lectures that clash, each pair once, with the first reason found for it. */
	public static List<Clash<Weekly>> within(List<Weekly> lectures) {
		List<Clash<Weekly>> clashes = new ArrayList<>();
		for (int i = 0; i < lectures.size(); i++) {
			for (int j = i + 1; j < lectures.size(); j++) {
				Weekly a = lectures.get(i);
				Weekly b = lectures.get(j);
				if (a.dayOfWeek() != b.dayOfWeek() || !overlap(a.start(), a.end(), b.start(), b.end())) {
					continue;
				}
				Kind kind = weeklyKind(a, b);
				if (kind != null) {
					clashes.add(new Clash<>(a, b, kind));
				}
			}
		}
		return clashes;
	}

	/** Whether two half-open time ranges share any time. Lectures that meet end to end do not overlap. */
	static boolean overlap(LocalTime startA, LocalTime endA, LocalTime startB, LocalTime endB) {
		return startA.isBefore(endB) && startB.isBefore(endA);
	}

	private static Kind weeklyKind(Weekly a, Weekly b) {
		if (a.facultyId().equals(b.facultyId())) {
			return Kind.FACULTY;
		}
		if (a.roomId() != null && a.roomId().equals(b.roomId())) {
			return Kind.ROOM;
		}
		if (a.divisionId().equals(b.divisionId())
				&& (a.batchId() == null || b.batchId() == null || a.batchId().equals(b.batchId()))) {
			return Kind.STUDENTS;
		}
		return null;
	}

}
