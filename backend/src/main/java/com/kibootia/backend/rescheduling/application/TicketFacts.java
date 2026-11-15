package com.kibootia.backend.rescheduling.application;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.academic.TicketAccessFacts;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.rescheduling.domain.TicketType;

/**
 * What the authorization policy needs to know about a ticket (integration contract): the lecture it concerns and the
 * people on it. The place, the kind and the author are those stored with the ticket; the batches the lecture is for and the
 * account of the faculty member are looked up as they are now, so a role change or a new batch applies at once. When the
 * teaching assignment is gone those two are empty, and a missing value never grants anything.
 */
public record TicketFacts(UUID tenantId, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
		Set<UUID> teachingFacultyUserIds, UUID authorUserId, Set<UUID> affectedFacultyUserIds,
		Set<UUID> requestedReviewerUserIds, boolean permanentChange) implements TicketAccessFacts {

	/** The faculty a ticket affects: those who teach the lecture, and the others it involves. */
	private static Set<UUID> affected(Set<UUID> teachers, Set<UUID> others) {
		Set<UUID> all = new HashSet<>(teachers);
		all.addAll(others);
		return Set.copyOf(all);
	}

	/**
	 * @param assignment the lecture's teaching assignment as it is now, or null when it no longer exists
	 * @param reviewers the people asked to review the ticket in its current revision
	 * @param teacherUserId the account of the faculty member who taught the lecture when the ticket was raised (the
	 *        substitute, if one was in front of it then), or null when they have none or were deleted
	 * @param othersAffected the accounts of the other faculty members the ticket affects: those whose lectures it also
	 *        changes (an exchange) and the one who takes the lecture (a substitute)
	 */
	public static TicketFacts of(Ticket ticket, AssignmentDetails assignment, Set<UUID> reviewers, UUID teacherUserId,
			Set<UUID> othersAffected) {
		Set<UUID> batches = (assignment == null) ? Set.of() : assignment.batchIds();
		Set<UUID> teachers = (teacherUserId == null) ? Set.of() : Set.of(teacherUserId);
		LectureRef lecture = ticket.lecture();
		return new TicketFacts(ticket.tenantId(), lecture.departmentId(), lecture.divisionId(), batches,
				lecture.sessionType(), teachers, ticket.authorUserId(), affected(teachers, othersAffected), Set.copyOf(reviewers),
				ticket.type() == TicketType.PERMANENT_CHANGE);
	}

}
