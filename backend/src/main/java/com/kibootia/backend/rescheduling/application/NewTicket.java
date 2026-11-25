package com.kibootia.backend.rescheduling.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.LectureSlot;

/** What is needed to raise a ticket ({@code revertsTicketId}: the applied ticket a revert undoes): the ticket, and the changes it makes (one, or two for an exchange). It starts as a draft. */
public record NewTicket(TicketType type, UUID authorUserId, UUID actingFacultyId, String description, LectureRef lecture,
		Instant expiresAt, List<Change> changes, UUID revertsTicketId) {

	/** A ticket that does not undo another. */
	public NewTicket(TicketType type, UUID authorUserId, UUID actingFacultyId, String description, LectureRef lecture,
			Instant expiresAt, List<Change> changes) {
		this(type, authorUserId, actingFacultyId, description, lecture, expiresAt, changes, null);
	}


	/**
	 * The change a new or edited ticket makes to a lecture.
	 *
	 * @param facultyId who teaches the lecture the change is about
	 */
	public record Change(ChangeOperation operation, UUID sourceOccurrenceId, long sourceRevision, LectureSlot target,
			String reason, UUID substituteFacultyId, UUID assignmentId, UUID facultyId) {

	}

}
