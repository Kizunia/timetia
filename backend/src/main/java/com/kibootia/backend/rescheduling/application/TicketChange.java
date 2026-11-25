package com.kibootia.backend.rescheduling.application;

import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * What a ticket does to one lecture (data model {@code TicketChangeItem}).
 *
 * @param sourceOccurrenceId the lecture the change is about; null for an extra lecture, which does not exist yet
 * @param sourceRevision the state of the lecture the change was planned against; if it has changed since, the change is
 *        refused
 * @param target where a move puts the lecture; null for a cancel
 * @param reason why a lecture is cancelled; required for a cancel
 * @param substituteFacultyId who takes the lecture, for a substitute; null otherwise
 * @param assignmentId the teaching assignment an extra lecture is for; null otherwise
 * @param facultyId who teaches the lecture the change is about, when the ticket was raised
 * @param createdOccurrenceId the lecture an applied extra ticket made; null before, and for any other change
 * @param before where the lecture was before the change was applied, kept so that it can be undone; null until applied
 * @param beforeFacultyId who taught the lecture before the change was applied; null until applied
 */
public record TicketChange(UUID id, UUID ticketId, ChangeOperation operation, UUID sourceOccurrenceId, long sourceRevision,
		LectureSlot target, String reason, UUID substituteFacultyId, UUID assignmentId, UUID createdOccurrenceId,
		UUID facultyId, LectureSlot before, UUID beforeFacultyId) {

	/** The lecture the change is about: the one it changes, or for an extra lecture the one it made, once it has. */
	public UUID occurrenceId() {
		return (this.sourceOccurrenceId != null) ? this.sourceOccurrenceId : this.createdOccurrenceId;
	}


}
