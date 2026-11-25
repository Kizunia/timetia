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
import com.kibootia.backend.reference.FacultyDetails;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;
import com.kibootia.backend.rescheduling.domain.Reviews;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureConflict;
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

	private final LectureCalendar calendar;

	TicketSupport(TicketStore tickets, AcademicDirectory directory, Authorization authorization, UserDirectory users,
			Clock clock, LectureCalendar calendar) {
		this.calendar = calendar;
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
		// The teacher is whoever taught the lecture when the ticket was raised, which a substitute may have changed. Everyone
		// else whose lecture the ticket changes (an exchange), and whoever it hands a lecture to (a substitute), is affected.
		UUID teacherFacultyId = ticket.lecture().facultyId();
		Set<UUID> facultyIds = new HashSet<>();
		facultyIds.add(teacherFacultyId);
		for (TicketChange change : this.tickets.changesOf(ticket.id())) {
			if (change.facultyId() != null) {
				facultyIds.add(change.facultyId());
			}
			if (change.substituteFacultyId() != null) {
				facultyIds.add(change.substituteFacultyId());
			}
		}
		Map<UUID, FacultyDetails> faculties = this.directory.faculties(facultyIds);
		Set<UUID> others = new HashSet<>();
		faculties.forEach((id, faculty) -> {
			if (!id.equals(teacherFacultyId) && faculty.userId() != null) {
				others.add(faculty.userId());
			}
		});
		return TicketFacts.of(ticket, assignment, this.tickets.reviewerUserIds(ticket.id(), ticket.revisionNo()),
				userOf(faculties.get(teacherFacultyId)), others);
	}

	private static UUID userOf(FacultyDetails faculty) {
		return (faculty == null) ? null : faculty.userId();
	}

	/** A ticket the caller may not see is not found, whatever they ask to do with it. */
	void requireSeen(TicketFacts facts) {
		if (!this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts)) {
			throw ticketNotFound();
		}
	}

	TicketDetail detail(Ticket ticket) {
		return new TicketDetail(ticket, this.tickets.changesOf(ticket.id()), this.tickets.latestCheck(ticket.id()),
				this.tickets.reviewsOf(ticket.id()));
	}

	/** The reviews of the revision the ticket is in now; only these count towards whether it is approved. */
	List<TicketReview> currentReviews(Ticket ticket) {
		return this.tickets.reviewsOf(ticket.id()).stream().filter((review) -> review.revisionNo() == ticket.revisionNo()).toList();
	}

	/** Checks the ticket's conflicts, records the result, and flags the ticket when something is in the way. */
	TicketDetail checked(Ticket ticket, CheckTrigger trigger) {
		List<TicketChange> changes = this.tickets.changesOf(ticket.id());
		List<LectureConflict> conflicts = this.calendar.check(toLectureChanges(changes));
		this.tickets.recordCheck(ticket.id(), trigger, conflicts);
		Ticket flagged = ticket.conflicted(!conflicts.isEmpty(), this.clock.instant());
		Ticket saved = (flagged.conflicted() == ticket.conflicted()) ? ticket : this.tickets.update(flagged);
		return new TicketDetail(saved, changes, conflicts, this.tickets.reviewsOf(ticket.id()));
	}

	List<LectureChange> toLectureChanges(List<TicketChange> changes) {
		return changes.stream().map(this::toLectureChange).toList();
	}

	LectureChange toLectureChange(TicketChange change) {
		return switch (change.operation()) {
			case CANCEL -> LectureChange.cancel(change.sourceOccurrenceId(), change.sourceRevision());
			case MOVE -> LectureChange.move(change.sourceOccurrenceId(), change.sourceRevision(), change.target());
			case REASSIGN_FACULTY -> LectureChange.reassign(change.sourceOccurrenceId(), change.sourceRevision(),
					change.substituteFacultyId());
			case CREATE_EXTRA -> LectureChange.create(change.assignmentId(), change.target());
			case RESTORE -> LectureChange.restore(change.sourceOccurrenceId(), change.sourceRevision(), change.target(),
					change.substituteFacultyId());
		};
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
	/**
	 * Whether the ticket may be applied now that it is under review: everyone asked has answered and none rejected it, and a
	 * permanent change also has the approval of an HOD, which nobody else's answer can stand in for.
	 */
	boolean approvedToApply(Ticket ticket) {
		List<TicketReview> reviews = currentReviews(ticket);
		if (Reviews.verdictOf(reviews.stream().map(TicketReview::status).toList()) != Reviews.Verdict.APPROVED) {
			return false;
		}
		return ticket.type() != TicketType.PERMANENT_CHANGE || reviews.stream()
			.anyMatch((review) -> review.capacity() == ReviewerCapacity.HOD && review.status() == ReviewStatus.APPROVED);
	}

	List<NewReview> validatedReviewers(UUID author, List<ReviewerRequest> requested) {
		return validatedReviewers(author, requested, false);
	}

	/**
	 * @param authorMayApprove whether the author may be asked in the capacity of HOD: an HOD who raises a permanent change for
	 *        their department approves it themselves
	 */
	List<NewReview> validatedReviewers(UUID author, List<ReviewerRequest> requested, boolean authorMayApprove) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (requested == null || requested.isEmpty()) {
			errors.add(new ApiFieldError("reviewers", "required", "Name at least one person to review the ticket."));
			throw invalid(errors);
		}
		Set<String> seen = new HashSet<>();
		Map<UUID, UserSummary> found = this.users.find(requested.stream().map(ReviewerRequest::userId).toList());
		for (ReviewerRequest reviewer : requested) {
			if (reviewer.userId().equals(author) && !(authorMayApprove && reviewer.capacity() == ReviewerCapacity.HOD)) {
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
