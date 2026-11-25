package com.kibootia.backend.rescheduling.application;

import java.util.Map;
import java.util.UUID;

import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.reference.FacultyDetails;
import com.kibootia.backend.timetable.LectureView;

/**
 * The names behind the ids of some tickets, looked up once for all of them. A name that is not in a map belongs to a record
 * that was deleted, or that the lookup was not asked for; the getters answer {@code null} for it and for a {@code null} id,
 * so a response can carry the id and no name.
 *
 * @param userNames display names of people, by user id
 * @param faculty faculty records, by faculty id
 * @param roomCodes room codes, by room id
 * @param assignments teaching assignments (subject, division, batch), by assignment id
 * @param lectures lectures as they are now, by lecture id; only filled for the detail of one ticket
 */
public record TicketNames(Map<UUID, String> userNames, Map<UUID, FacultyDetails> faculty, Map<UUID, String> roomCodes,
		Map<UUID, AssignmentDetails> assignments, Map<UUID, LectureView> lectures) {

	/** No names at all: every getter answers {@code null}. */
	public static final TicketNames NONE = new TicketNames(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());

	public String userName(UUID userId) {
		return (userId == null) ? null : this.userNames.get(userId);
	}

	public String facultyName(UUID facultyId) {
		FacultyDetails found = (facultyId == null) ? null : this.faculty.get(facultyId);
		return (found == null) ? null : found.name();
	}

	public String roomCode(UUID roomId) {
		return (roomId == null) ? null : this.roomCodes.get(roomId);
	}

	public AssignmentDetails assignment(UUID assignmentId) {
		return (assignmentId == null) ? null : this.assignments.get(assignmentId);
	}

	public LectureView lecture(UUID lectureId) {
		return (lectureId == null) ? null : this.lectures.get(lectureId);
	}

}
