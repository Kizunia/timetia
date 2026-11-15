package com.kibootia.backend.timetable;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.LectureAccessFacts;
import com.kibootia.backend.academic.SessionType;

/**
 * One lecture on one date as it is now: the base timetable's slot with any applied change laid over it, who it is for and
 * who teaches it, with everything the authorization policy needs. It is what the live timetable shows and what the
 * rescheduling module reads, so the two never disagree about a lecture.
 *
 * <p>The batches the lecture is for and the account the faculty member signs in as are those of the reference data now, so a
 * new batch or a newly linked account applies at once. A name or code is null when what it describes was deleted since the
 * timetable was published.
 *
 * @param slot where and when it is held now
 * @param baseSlot where and when the base timetable has it; differs from {@code slot} once a change moved it
 * @param cancelled whether a change cancelled it; a cancelled lecture is shown, marked, and holds nothing
 * @param changedByTicketId the ticket whose change is laid over it, or null when it is as the base timetable has it
 * @param revision changes every time the lecture changes, so a change made against an older state is noticed
 * @param batchId the batch of a lab or tutorial, or null for a theory lecture, which is for the whole division
 * @param batchIds every batch the lecture is for: all the batches of the division for a theory lecture
 * @param roomCode the code of the room it is held in now, or null when online
 * @param startsAt the instant it starts now, in the campus time zone
 */
public record LectureView(UUID id, UUID tenantId, UUID assignmentId, LectureSlot slot, LectureSlot baseSlot,
		boolean cancelled, UUID changedByTicketId, long revision, UUID departmentId, UUID divisionId, String divisionCode,
		UUID batchId, String batchCode, Set<UUID> batchIds, SessionType sessionType, UUID subjectId, String subjectCode,
		String subjectName, UUID facultyId, String facultyName, Set<UUID> teachingFacultyUserIds, String roomCode,
		Instant startsAt) implements LectureAccessFacts {

	/** Whether a change has been laid over this lecture. */
	public boolean changed() {
		return this.changedByTicketId != null;
	}

}
