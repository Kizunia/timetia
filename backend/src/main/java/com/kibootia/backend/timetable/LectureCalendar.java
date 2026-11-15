package com.kibootia.backend.timetable;

import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * The live timetable as the rescheduling module uses it: read a lecture as it is now, ask whether changes can be made, and
 * make them. The timetable owns the lectures and the resources they hold, so the rules that keep a teacher, a room or a
 * group of students from being booked twice live here, and a change is made all or nothing.
 *
 * <p>The module that calls this decides <em>who</em> may change a lecture and <em>whether</em> a ticket is in a state to be
 * applied; the calls check no permission of their own. They are not for a request that has not been authorized.
 */
public interface LectureCalendar {

	/** The lecture as it is now, or empty when there is no such live lecture. */
	Optional<LectureView> lecture(UUID occurrenceId);

	/** The time zone of the campus, in which hours are read and a lecture is said to have started. */
	ZoneId zone();

	/**
	 * What would stop these changes, without making them. Changes made together are checked together: a lecture moving out
	 * of a slot frees it for another that moves in. Empty means they can be made.
	 */
	List<LectureConflict> check(List<LectureChange> changes);

	/**
	 * Makes the changes, all or none, and records the ticket that made them. The lectures are locked and the changes checked
	 * again first.
	 *
	 * @throws ChangeConflictException when something stops them; nothing was written
	 */
	void apply(List<LectureChange> changes, UUID ticketId);

}
