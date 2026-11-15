package com.kibootia.backend.rescheduling.application;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.academic.TicketAccessFacts;
import com.kibootia.backend.reference.AssignmentDetails;

/**
 * What the authorization policy needs to know about a ticket (integration contract): the lecture it concerns and the
 * people on it. The place, the kind and the author are those stored with the ticket; the batches the lecture is for and the
 * account of the faculty member are looked up as they are now, so a role change or a new batch applies at once. When the
 * teaching assignment is gone those two are empty, and a missing value never grants anything.
 */
public record TicketFacts(UUID tenantId, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
		Set<UUID> teachingFacultyUserIds, UUID authorUserId, Set<UUID> affectedFacultyUserIds,
		Set<UUID> requestedReviewerUserIds, boolean permanentChange) implements TicketAccessFacts {

	/** @param assignment the lecture's teaching assignment as it is now, or null when it no longer exists */
	public static TicketFacts of(Ticket ticket, AssignmentDetails assignment) {
		Set<UUID> batches = (assignment == null) ? Set.of() : assignment.batchIds();
		Set<UUID> teachers = (assignment == null || assignment.facultyUserId() == null) ? Set.of()
				: Set.of(assignment.facultyUserId());
		LectureRef lecture = ticket.lecture();
		return new TicketFacts(ticket.tenantId(), lecture.departmentId(), lecture.divisionId(), batches,
				lecture.sessionType(), teachers, ticket.authorUserId(), teachers, Set.of(), false);
	}

}
