package com.kibootia.backend.timetable.application;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.reference.RoomDetails;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;

/**
 * Turns stored lectures into the {@link LectureView} that the live timetable and the rescheduling module both read. What a
 * lecture is for and who teaches it are looked up in the reference data as it is now, once for a whole page and not once per
 * lecture, so a new batch or a newly linked account applies at once. Package-private: it is a helper of the use cases, not a
 * use case, so it checks no permission.
 */
@Component
class LectureViewFactory {

	private final AcademicDirectory directory;

	private final TimetableProperties properties;

	LectureViewFactory(AcademicDirectory directory, TimetableProperties properties) {
		this.directory = directory;
		this.properties = properties;
	}

	List<LectureView> viewsOf(List<Occurrence> occurrences) {
		Map<UUID, AssignmentDetails> assignments = this.directory
			.assignments(occurrences.stream().map(Occurrence::assignmentId).collect(Collectors.toSet()));
		Map<UUID, RoomDetails> rooms = this.directory.rooms(occurrences.stream()
			.map((occurrence) -> occurrence.slot().roomId())
			.filter(Objects::nonNull)
			.collect(Collectors.toSet()));
		// The maps are immutable, which refuse a null key, and an online lecture has no room.
		return occurrences.stream()
			.map((occurrence) -> viewOf(occurrence, assignments.get(occurrence.assignmentId()),
					(occurrence.slot().roomId() == null) ? null : rooms.get(occurrence.slot().roomId())))
			.toList();
	}

	/** @param assignment the lecture's teaching assignment, or null when it no longer exists; likewise {@code room} */
	LectureView viewOf(Occurrence occurrence, AssignmentDetails assignment, RoomDetails room) {
		LectureSlot slot = occurrence.slot();
		Instant startsAt = slot.date().atTime(slot.startTime()).atZone(this.properties.zone()).toInstant();
		boolean known = assignment != null;
		Set<UUID> teachers = (known && assignment.facultyUserId() != null) ? Set.of(assignment.facultyUserId()) : Set.of();
		return new LectureView(occurrence.id(), occurrence.tenantId(), occurrence.assignmentId(), slot,
				occurrence.baseSlot(), occurrence.cancelled(), occurrence.changedByTicketId(), occurrence.revision(),
				occurrence.departmentId(), occurrence.divisionId(), known ? assignment.divisionCode() : null,
				occurrence.batchId(), known ? assignment.batchCode() : null, known ? assignment.batchIds() : Set.of(),
				occurrence.sessionType(), known ? assignment.subjectId() : null, known ? assignment.subjectCode() : null,
				known ? assignment.subjectName() : null, occurrence.facultyId(), known ? assignment.facultyName() : null,
				teachers, (room == null) ? null : room.code(), startsAt);
	}

}
