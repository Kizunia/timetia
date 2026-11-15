package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.rescheduling.application.Progress.Outcome;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;
import com.kibootia.backend.rescheduling.domain.Reviews;
import com.kibootia.backend.rescheduling.domain.Reviews.Verdict;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureView;

/**
 * The review of a ticket (approval model). The author may send a draft to any combination of people for review instead of
 * applying it; each answers, and the ticket is applied when everyone has answered and nobody rejected it. One rejection
 * rejects it at once. A rejected ticket is edited and sent again, or the author takes the rejection to the HOD.
 *
 * <p>Who may answer is the policy's: the person must have been asked, for this ticket in its current revision, and hold the
 * review or reject permission for the lecture (a CR for a theory lecture of the division, a BR for a lab or tutorial of the
 * batch, an HOD of the department, a faculty member anywhere). Asking someone in a capacity they do not hold changes
 * nothing. The state rules are this class's: a ticket is answered only while it is under review, and a lecture that has
 * started expires the ticket.
 */
@Service
public class TicketReviewService {

	public static final String NOT_IN_REVIEW = "rescheduling.ticket_not_in_review";

	public static final String NOT_REJECTED = "rescheduling.ticket_not_rejected";

	public static final String ALREADY_DECIDED = "rescheduling.review_already_decided";

	/** What a reviewer may answer. */
	public enum Decision {

		APPROVE(ReviewStatus.APPROVED), REJECT(ReviewStatus.REJECTED), ACKNOWLEDGE(ReviewStatus.ACKNOWLEDGED);

		private final ReviewStatus status;

		Decision(ReviewStatus status) {
			this.status = status;
		}

		ReviewStatus status() {
			return this.status;
		}

	}

	private final TicketStore tickets;

	private final LectureCalendar calendar;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final Clock clock;

	private final TicketSupport support;

	private final TicketApplier applier;

	TicketReviewService(TicketStore tickets, LectureCalendar calendar, Authorization authorization, AuditLog auditLog,
			Clock clock, TicketSupport support, TicketApplier applier) {
		this.tickets = tickets;
		this.calendar = calendar;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.clock = clock;
		this.support = support;
		this.applier = applier;
	}

	/**
	 * Sends a draft for review by these people. A ticket with a conflict can still be sent, like a pull request with merge
	 * conflicts: the review goes on, and the ticket cannot be applied while the conflict stands.
	 */
	@Transactional
	public Progress submit(UUID id, List<ReviewerRequest> reviewers) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		if (ticket.status() != TicketStatus.DRAFT) {
			throw ApiException.conflict(TicketService.NOT_A_DRAFT, "Only a draft can be sent for review.");
		}
		List<NewReview> asked = this.support.validatedReviewers(ticket.authorUserId(), reviewers);
		if (this.applier.due(ticket)) {
			return this.applier.expire(ticket);
		}
		requireLectureChangeable(ticket);
		this.tickets.insertReviews(id, ticket.revisionNo(), asked);
		Ticket submitted = this.tickets.update(ticket.submitted());
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.submitted", "ticket", id)
			.withChange("from", ticket.status().name())
			.withChange("to", submitted.status().name())
			.withChange("reviewers", String.valueOf(asked.size())));
		return new Progress(Outcome.DONE, this.support.detail(submitted));
	}

	/**
	 * A requested reviewer answers. An approval or an acknowledgement may complete the review, in which case the ticket is
	 * applied in the same transaction, unless something is in the way, when it stays under review, flagged. A rejection needs a
	 * reason and rejects the ticket at once.
	 */
	@Transactional
	public Progress review(UUID id, Decision decision, String reason) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		boolean rejecting = decision == Decision.REJECT;
		this.authorization.require(rejecting ? AcademicPermissions.TICKETS_REJECT : AcademicPermissions.TICKETS_REVIEW, facts);
		if (ticket.status() != TicketStatus.IN_REVIEW) {
			throw ApiException.conflict(NOT_IN_REVIEW, "The ticket is " + ticket.status().name().toLowerCase().replace('_', ' ')
					+ ", not under review.");
		}
		if (rejecting && (reason == null || reason.isBlank())) {
			throw invalid("reason", "required", "A rejection says why.");
		}
		UUID me = this.authorization.currentUserId();
		TicketReview mine = this.support.currentReviews(ticket)
			.stream()
			.filter((review) -> review.reviewerUserId().equals(me))
			.findFirst()
			.orElseThrow(() -> new AccessDeniedException("You were not asked to review this ticket"));
		if (mine.status() != ReviewStatus.PENDING) {
			throw ApiException.conflict(ALREADY_DECIDED, "You have already answered this review.");
		}
		if (this.applier.due(ticket)) {
			return this.applier.expire(ticket);
		}

		this.tickets.decide(mine.id(), decision.status(), (reason == null || reason.isBlank()) ? null : reason.strip(),
				this.clock.instant());
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.reviewed", "ticket", id)
			.withChange("decision", decision.name())
			.withChange("capacity", mine.capacity().name()));
		if (rejecting) {
			Ticket rejected = this.tickets.update(ticket.rejected());
			this.auditLog.record(AuditEntry.of("rescheduling.ticket.rejected", "ticket", id)
				.withChange("from", ticket.status().name())
				.withChange("to", rejected.status().name()));
			return new Progress(Outcome.DONE, this.support.detail(rejected));
		}
		Verdict verdict = Reviews
			.verdictOf(this.support.currentReviews(ticket).stream().map(TicketReview::status).toList());
		return (verdict == Verdict.APPROVED) ? this.applier.applyNow(ticket, ApprovalPath.REVIEWED)
				: new Progress(Outcome.DONE, this.support.detail(ticket));
	}

	/**
	 * The author takes a rejection to the HOD, who becomes the reviewer and decides: the ticket goes back under review, one
	 * revision later, with only the HOD to answer. The earlier rejection stays in the history.
	 */
	@Transactional
	public Progress escalate(UUID id, UUID hodUserId) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		if (ticket.status() != TicketStatus.REJECTED) {
			throw ApiException.conflict(NOT_REJECTED, "Only a rejected ticket can be taken to the HOD.");
		}
		List<NewReview> hod = this.support.validatedReviewers(ticket.authorUserId(),
				List.of(new ReviewerRequest(hodUserId, ReviewerCapacity.HOD)));
		if (this.applier.due(ticket)) {
			return this.applier.expire(ticket);
		}
		requireLectureChangeable(ticket);
		TicketReview rejection = this.support.currentReviews(ticket)
			.stream()
			.filter((review) -> review.status() == ReviewStatus.REJECTED)
			.findFirst()
			.orElseThrow();
		Ticket escalated = this.tickets.update(ticket.escalated());
		this.tickets.insertReviews(id, escalated.revisionNo(),
				List.of(new NewReview(hod.get(0).reviewerUserId(), ReviewerCapacity.HOD, true, rejection.id())));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.escalated", "ticket", id)
			.withChange("from", ticket.status().name())
			.withChange("to", escalated.status().name()));
		return new Progress(Outcome.DONE, this.support.detail(escalated));
	}

	/** A ticket cannot go back under review for a lecture that is gone, cancelled or has started. */
	private void requireLectureChangeable(Ticket ticket) {
		TicketChange change = this.tickets.changeOf(ticket.id());
		LectureView lecture = this.calendar.lecture(change.sourceOccurrenceId())
			.orElseThrow(() -> ApiException.conflict(TicketService.LECTURE_NOT_FOUND,
					"The lecture this ticket concerns no longer exists."));
		this.support.requireChangeable(lecture);
	}

	private static ApiException invalid(String field, String code, String message) {
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
				List.of(new ApiFieldError(field, code, message)));
	}

}
