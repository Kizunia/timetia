package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.directory.UserDirectory;
import com.kibootia.backend.platform.directory.UserSummary;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.timetable.LectureChange;
import com.kibootia.backend.timetable.LectureView;

/**
 * What the use cases of tickets have in common: loading a ticket, the facts the policy reads, "a ticket you may not see does
 * not exist", the rule that a lecture that has started or is cancelled cannot be changed, and the check of the people asked to
 * review. Package-private: it is a helper of the use cases, not a use case, so it makes no decision of its own about who may
 * do what; the use cases ask the policy.
 */
@Component
class TicketSupport {

	private final TicketStore tickets;

	private final AcademicDirectory directory;

	private final Authorization authorization;

	private final UserDirectory users;

	private final Clock clock;

	TicketSupport(TicketStore tickets, AcademicDirectory directory, Authorization authorization, UserDirectory users,
			Clock clock) {
		this.tickets = tickets;
		this.directory = directory;
		this.authorization = authorization;
		this.users = users;
		this.clock = clock;
	}

	Ticket load(UUID id) {
		return this.tickets.find(id).orElseThrow(TicketSupport::ticketNotFound);
	}

	/** The facts the policy reads: the ticket, its lecture as it is now, and the people asked to review it. */
	TicketFacts facts(Ticket ticket) {
		UUID assignmentId = ticket.lecture().assignmentId();
		AssignmentDetails assignment = this.directory.assignments(Set.of(assignmentId)).get(assignmentId);
		return TicketFacts.of(ticket, assignment, this.tickets.reviewerUserIds(ticket.id(), ticket.revisionNo()));
	}

	/** A ticket the caller may not see is not found, whatever they ask to do with it. */
	void requireSeen(TicketFacts facts) {
		if (!this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts)) {
			throw ticketNotFound();
		}
	}

	TicketDetail detail(Ticket ticket) {
		return new TicketDetail(ticket, this.tickets.changeOf(ticket.id()), this.tickets.latestCheck(ticket.id()),
				this.tickets.reviewsOf(ticket.id()));
	}

	/** The reviews of the revision the ticket is in now; only these count towards whether it is approved. */
	List<TicketReview> currentReviews(Ticket ticket) {
		return this.tickets.reviewsOf(ticket.id()).stream().filter((review) -> review.revisionNo() == ticket.revisionNo()).toList();
	}

	LectureChange toLectureChange(TicketChange change) {
		return (change.operation() == ChangeOperation.CANCEL)
				? LectureChange.cancel(change.sourceOccurrenceId(), change.sourceRevision())
				: LectureChange.move(change.sourceOccurrenceId(), change.sourceRevision(), change.target());
	}

	/** A lecture that is cancelled or has started cannot be changed. */
	void requireChangeable(LectureView lecture) {
		if (lecture.cancelled()) {
			throw ApiException.conflict(TicketService.LECTURE_CANCELLED, "The lecture is cancelled.");
		}
		if (!lecture.startsAt().isAfter(this.clock.instant())) {
			throw ApiException.conflict(TicketService.LECTURE_STARTED, "The lecture has started, so it can no longer be changed.");
		}
	}

	/**
	 * Checks the people the author asks to review and returns them as reviews to add. There must be at least one, each once,
	 * not the author, and each a person who can sign in. What a person may do as a reviewer is decided when they answer, by
	 * the role and place they hold, so asking someone who cannot review leaves the ticket waiting; it is the author's to fix.
	 */
	List<NewReview> validatedReviewers(UUID author, List<ReviewerRequest> requested) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (requested == null || requested.isEmpty()) {
			errors.add(new ApiFieldError("reviewers", "required", "Name at least one person to review the ticket."));
			throw invalid(errors);
		}
		Set<String> seen = new HashSet<>();
		Map<UUID, UserSummary> found = this.users.find(requested.stream().map(ReviewerRequest::userId).toList());
		for (ReviewerRequest reviewer : requested) {
			if (reviewer.userId().equals(author)) {
				errors.add(new ApiFieldError("reviewers", "reviewer_is_author", "The author cannot review their own ticket."));
			}
			else if (!found.containsKey(reviewer.userId()) || !found.get(reviewer.userId()).active()) {
				errors.add(new ApiFieldError("reviewers", "unknown_reviewer",
						"A person asked to review does not exist or cannot sign in."));
			}
			if (!seen.add(reviewer.userId() + ":" + reviewer.capacity())) {
				errors.add(new ApiFieldError("reviewers", "duplicate_reviewer", "A person is asked once in each capacity."));
			}
		}
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
		return requested.stream().map((reviewer) -> NewReview.of(reviewer.userId(), reviewer.capacity())).toList();
	}

	private static ApiException invalid(List<ApiFieldError> errors) {
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
	}

	static ApiException ticketNotFound() {
		return ApiException.notFound(TicketService.TICKET_NOT_FOUND, "There is no ticket with that ID.");
	}

}
