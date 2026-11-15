package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.academic.AcademicScopes;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.Reach;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.rescheduling.application.TicketQuery.Visibility;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.Reviews;
import com.kibootia.backend.rescheduling.domain.Reviews.Verdict;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;

/**
 * The use cases of tickets that reschedule or cancel one lecture: raising and editing them, applying them, withdrawing them,
 * and reading them. Sending a ticket for review, answering a review and taking a rejection to the HOD are
 * {@link TicketReviewService}'s. A ticket is a proposal: it is checked when it is raised and again when it is applied, and
 * it changes nothing until then. Who may do what is decided by the policy of the {@code academic} module from the lecture and
 * the people on the ticket; the rules of state, whether the lecture has started or the ticket is a draft, are this class's.
 *
 * <p>What the policy decides here:
 * <ul>
 * <li>raising a ticket: the teacher of the lecture, or the HOD of its department acting for them
 * ({@code create} and {@code create-on-behalf}, or {@code cancel} and {@code cancel-on-behalf} for a cancellation);</li>
 * <li>applying it: only its author. A draft is applied without a review: a reschedule needs {@code self-approve}, held by a
 * faculty member who teaches the lecture, which an HOD does not hold, and a cancellation is applied directly by whoever may
 * cancel the lecture. A ticket under review is applied when every reviewer has answered and none rejected it;</li>
 * <li>editing and withdrawing it: its author ({@code edit-own});</li>
 * <li>seeing it: the people on it, including the people asked to review it, and the roles whose place covers the lecture.</li>
 * </ul>
 * A ticket the caller may not see is answered as not found.
 */
@Service
public class TicketService {

	public static final String TICKET_NOT_FOUND = "rescheduling.ticket_not_found";

	public static final String LECTURE_NOT_FOUND = "rescheduling.lecture_not_found";

	public static final String LECTURE_STARTED = "rescheduling.lecture_started";

	public static final String LECTURE_CANCELLED = "rescheduling.lecture_cancelled";

	public static final String NOT_A_DRAFT = "rescheduling.ticket_not_draft";

	public static final String NOT_EDITABLE = "rescheduling.ticket_not_editable";

	public static final String REVIEWS_PENDING = "rescheduling.reviews_pending";

	public static final String STALE_TICKET = "rescheduling.stale_ticket";

	private static final Set<TicketStatus> EDITABLE = Set.of(TicketStatus.DRAFT, TicketStatus.IN_REVIEW, TicketStatus.REJECTED);

	private final TicketStore tickets;

	private final LectureCalendar calendar;

	private final AcademicDirectory directory;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final Clock clock;

	private final TicketSupport support;

	private final TicketApplier applier;

	TicketService(TicketStore tickets, LectureCalendar calendar, AcademicDirectory directory, Authorization authorization,
			AuditLog auditLog, Clock clock, TicketSupport support, TicketApplier applier) {
		this.tickets = tickets;
		this.calendar = calendar;
		this.directory = directory;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.clock = clock;
		this.support = support;
		this.applier = applier;
	}

	/**
	 * What a person asks for when raising a ticket.
	 *
	 * @param target where a reschedule puts the lecture; null for a cancellation
	 * @param reason why a lecture is cancelled; required for a cancellation
	 */
	public record Draft(TicketType type, UUID lectureId, String description, LectureSlot target, String reason) {

	}

	/**
	 * What the author changes when editing. {@code expectedVersion} is the version of the ticket they last read.
	 *
	 * @param reviewers for a ticket under review, the people to ask from now on; null leaves them as they are
	 */
	public record Edit(String description, LectureSlot target, String reason, List<ReviewerRequest> reviewers,
			long expectedVersion) {

	}

	/** Which tickets a list asks for. {@code mine} narrows it to the tickets the caller wrote. */
	public record TicketFilter(TicketStatus status, UUID lectureId, boolean mine) {

	}

	// --- raising and editing ----------------------------------------------------------------------------------------

	@Transactional
	public TicketDetail create(Draft draft) {
		UUID me = this.authorization.currentUserId();
		LectureView lecture = this.calendar.lecture(draft.lectureId()).orElseThrow(TicketService::lectureNotFound);
		if (!mayViewLecture(lecture)) {
			throw lectureNotFound();
		}
		boolean cancel = draft.type() == TicketType.CANCEL;
		boolean teaches = this.authorization.can(cancel ? AcademicPermissions.TICKETS_CANCEL : AcademicPermissions.TICKETS_CREATE,
				lecture);
		if (!teaches) {
			this.authorization.require(cancel ? AcademicPermissions.TICKETS_CANCEL_ON_BEHALF
					: AcademicPermissions.TICKETS_CREATE_ON_BEHALF, lecture);
		}
		this.support.requireChangeable(lecture);
		NewTicket.Change change = validated(draft.type(), lecture, draft.target(), draft.reason());
		LectureRef ref = new LectureRef(lecture.assignmentId(), lecture.departmentId(), lecture.divisionId(),
				lecture.batchId(), lecture.sessionType(), lecture.facultyId());
		Ticket ticket = this.tickets.insert(new NewTicket(draft.type(), me, teaches ? null : lecture.facultyId(),
				clean(draft.description()), ref, expiresAt(lecture, change.target()), change));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", draft.type().name()));
		return checked(ticket, CheckTrigger.SUBMISSION);
	}

	/**
	 * Replaces the plan of a ticket that is not yet applied, and checks it again. A rejected ticket becomes a draft again, one
	 * revision later, to be sent for review once more. A ticket under review keeps its approvals when only the description or
	 * the people asked change, and loses them when the lecture's destination changes, because what was approved is no longer
	 * what is proposed; the reviewers are asked again.
	 */
	@Transactional
	public TicketDetail edit(UUID id, Edit edit) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		if (!EDITABLE.contains(ticket.status())) {
			throw ApiException.conflict(NOT_EDITABLE, "A ticket that is " + words(ticket.status()) + " cannot be edited.");
		}
		if (ticket.version() != edit.expectedVersion()) {
			throw ApiException.conflict(STALE_TICKET, "The ticket was changed after you read it. Read it again.");
		}
		if (edit.reviewers() != null && ticket.status() != TicketStatus.IN_REVIEW) {
			throw invalid(List.of(new ApiFieldError("reviewers", "not_in_review",
					"The people to ask can be changed only while the ticket is under review.")));
		}
		TicketChange current = this.tickets.changeOf(id);
		LectureView lecture = this.calendar.lecture(current.sourceOccurrenceId()).orElseThrow(TicketService::lectureGone);
		this.support.requireChangeable(lecture);
		// The plan is made against the lecture as it is now, so an edit also brings a stale ticket up to date.
		NewTicket.Change change = validated(ticket.type(), lecture, edit.target(), edit.reason());
		List<NewReview> reviewers = (edit.reviewers() == null) ? null
				: this.support.validatedReviewers(ticket.authorUserId(), edit.reviewers());

		boolean underReview = ticket.status() == TicketStatus.IN_REVIEW;
		boolean approvalsCleared = underReview && planChanged(current, change);
		this.tickets.replaceChange(id, change);
		Ticket edited = ticket.edited(clean(edit.description()), expiresAt(lecture, change.target()));
		if (ticket.status() == TicketStatus.REJECTED) {
			edited = edited.resubmitted();
		}
		edited = this.tickets.update(edited);
		if (approvalsCleared) {
			this.tickets.resetDecisions(id, edited.revisionNo());
		}
		if (reviewers != null) {
			this.tickets.syncReviewers(id, edited.revisionNo(), reviewers);
		}
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.edited", "ticket", id)
			.withChange("from", ticket.status().name())
			.withChange("to", edited.status().name())
			.withChange("approvalsCleared", String.valueOf(approvalsCleared)));
		return checked(edited, CheckTrigger.SUBMISSION);
	}

	// --- applying ---------------------------------------------------------------------------------------------------

	/**
	 * Applies the ticket by its author. A draft is applied without a review (self-approval); a ticket under review, once every
	 * reviewer has answered and none rejected it. The conflicts are checked again with the lecture locked, because something may
	 * have been done since; the change is made only if nothing is in the way.
	 */
	@Transactional
	public Progress apply(UUID id) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		return switch (ticket.status()) {
			case DRAFT -> {
				if (ticket.type() == TicketType.CANCEL) {
					if (!this.authorization.can(AcademicPermissions.TICKETS_CANCEL, facts)) {
						this.authorization.require(AcademicPermissions.TICKETS_CANCEL_ON_BEHALF, facts);
					}
				}
				else {
					this.authorization.require(AcademicPermissions.TICKETS_SELF_APPROVE, facts);
				}
				yield this.applier.applyNow(ticket, ApprovalPath.SELF);
			}
			case IN_REVIEW -> {
				if (reviewVerdict(ticket) != Verdict.APPROVED) {
					throw ApiException.conflict(REVIEWS_PENDING, "Not everyone asked to review the ticket has answered yet.");
				}
				yield this.applier.applyNow(ticket, ApprovalPath.REVIEWED);
			}
			default -> throw ApiException.conflict(NOT_A_DRAFT, "The ticket is " + words(ticket.status())
					+ "; only a draft, or a ticket whose reviews are in, can be applied.");
		};
	}

	@Transactional
	public TicketDetail withdraw(UUID id) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		this.support.requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		if (!ticket.status().canBecome(TicketStatus.WITHDRAWN)) {
			throw ApiException.conflict(NOT_A_DRAFT, "A ticket that is " + words(ticket.status()) + " cannot be withdrawn.");
		}
		// Withdrawing after someone approved needs no extra step, but the history keeps that it happened.
		boolean afterApproval = this.support.currentReviews(ticket)
			.stream()
			.anyMatch((review) -> review.status() == ReviewStatus.APPROVED);
		Ticket withdrawn = this.tickets.update(ticket.withdrawn(this.clock.instant()));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.withdrawn", "ticket", id)
			.withChange("from", ticket.status().name())
			.withChange("to", withdrawn.status().name())
			.withChange("afterApproval", String.valueOf(afterApproval)));
		return this.support.detail(withdrawn);
	}

	// --- reading ----------------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public TicketDetail get(UUID id) {
		Ticket ticket = this.support.load(id);
		TicketFacts facts = this.support.facts(ticket);
		if (!this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts)) {
			throw TicketSupport.ticketNotFound();
		}
		return this.support.detail(ticket);
	}

	/**
	 * The tickets the caller may see: those they are on (as the author, as the teacher of the lecture, or as a person asked to
	 * review it), and those whose lecture their role covers. Filtered in the query, from {@link Authorization#reach}, before
	 * paging.
	 */
	@Transactional(readOnly = true)
	public Page<TicketDetail> list(TicketFilter filter, Pageable pageable) {
		UUID me = this.authorization.currentUserId();
		Reach inScope = this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE);
		boolean onOwn = !this.authorization.reach(AcademicPermissions.TICKETS_VIEW).isEmpty();
		Visibility visibility = new Visibility(inScope.everywhere(), inScope.idsOf(AcademicScopes.DEPARTMENT),
				inScope.idsOf(AcademicScopes.DIVISION), inScope.idsOf(AcademicScopes.BATCH), onOwn ? me : null,
				onOwn ? this.directory.facultyIdsOf(me) : Set.of(), onOwn ? me : null);
		if (visibility.isEmpty()) {
			return Page.empty(pageable);
		}
		Page<Ticket> page = this.tickets.search(
				new TicketQuery(filter.status(), filter.lectureId(), filter.mine() ? me : null, visibility), pageable);
		Map<UUID, TicketChange> changes = this.tickets
			.changesOf(page.getContent().stream().map(Ticket::id).collect(Collectors.toList()));
		return page.map((ticket) -> new TicketDetail(ticket, changes.get(ticket.id()), List.of(), List.of()));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private boolean mayViewLecture(LectureView lecture) {
		return this.authorization.can(AcademicPermissions.LIVE_VIEW_ALL)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW, lecture)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW_OWN, lecture);
	}

	private Verdict reviewVerdict(Ticket ticket) {
		return Reviews.verdictOf(this.support.currentReviews(ticket).stream().map(TicketReview::status).toList());
	}

	/** Checks the ticket's conflicts, records the result, and flags the ticket when something is in the way. */
	private TicketDetail checked(Ticket ticket, CheckTrigger trigger) {
		TicketChange change = this.tickets.changeOf(ticket.id());
		List<LectureConflict> conflicts = this.calendar.check(List.of(this.support.toLectureChange(change)));
		this.tickets.recordCheck(ticket.id(), trigger, conflicts);
		Ticket flagged = ticket.conflicted(!conflicts.isEmpty(), this.clock.instant());
		Ticket saved = (flagged.conflicted() == ticket.conflicted()) ? ticket : this.tickets.update(flagged);
		return new TicketDetail(saved, change, conflicts, this.tickets.reviewsOf(ticket.id()));
	}

	/** Whether the edit changes what is proposed: what is done, or where the lecture goes. */
	private static boolean planChanged(TicketChange current, NewTicket.Change change) {
		if (current.operation() != change.operation()) {
			return true;
		}
		LectureSlot before = current.target();
		LectureSlot after = change.target();
		if (before == null || after == null) {
			return before != after;
		}
		return !before.date().equals(after.date()) || !before.startTime().equals(after.startTime())
				|| !before.endTime().equals(after.endTime()) || before.mode() != after.mode()
				|| !Objects.equals(before.roomId(), after.roomId());
	}

	/** The earliest lecture the ticket involves: when it lapses if not applied. */
	private Instant expiresAt(LectureView lecture, LectureSlot target) {
		if (target == null) {
			return lecture.startsAt();
		}
		Instant targetStart = LocalDateTime.of(target.date(), target.startTime()).atZone(this.calendar.zone()).toInstant();
		return targetStart.isBefore(lecture.startsAt()) ? targetStart : lecture.startsAt();
	}

	/** Checks what the person asked for and returns the change it makes; every bad field is reported together. */
	private NewTicket.Change validated(TicketType type, LectureView lecture, LectureSlot target, String reason) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (type == TicketType.CANCEL) {
			if (reason == null || reason.isBlank()) {
				errors.add(new ApiFieldError("reason", "required", "A lecture is cancelled with a reason."));
			}
			if (target != null) {
				errors.add(new ApiFieldError("target", "not_allowed", "A cancellation names no new slot."));
			}
			requireValid(errors);
			return new NewTicket.Change(ChangeOperation.CANCEL, lecture.id(), lecture.revision(), null, reason.strip());
		}
		if (target == null) {
			errors.add(new ApiFieldError("target", "required", "A reschedule says where the lecture goes."));
		}
		else {
			validateTarget(lecture, target, errors);
		}
		requireValid(errors);
		return new NewTicket.Change(ChangeOperation.MOVE, lecture.id(), lecture.revision(), target, clean(reason));
	}

	private void validateTarget(LectureView lecture, LectureSlot target, List<ApiFieldError> errors) {
		if (target.mode() == LectureMode.IN_PERSON) {
			if (target.roomId() == null) {
				errors.add(new ApiFieldError("target.roomId", "room_required", "An in-person lecture needs a room."));
			}
			else if (!this.directory.rooms(Set.of(target.roomId())).containsKey(target.roomId())) {
				errors.add(new ApiFieldError("target.roomId", "unknown_room", "There is no room with that ID."));
			}
			if (target.meetingLink() != null) {
				errors.add(new ApiFieldError("target.meetingLink", "link_not_allowed", "Only an online lecture has a link."));
			}
		}
		else if (target.roomId() != null) {
			errors.add(new ApiFieldError("target.roomId", "room_not_allowed", "An online lecture has no room."));
		}
		if (target.startTime() != null && target.endTime() != null && target.date() != null) {
			if (target.endTime().getHour() - target.startTime().getHour() != lecture.slot().hours()) {
				errors.add(new ApiFieldError("target.endTime", "same_length",
						"The lecture lasts " + lecture.slot().hours() + " hour(s), and a move does not change that."));
			}
			Instant start = LocalDateTime.of(target.date(), target.startTime()).atZone(this.calendar.zone()).toInstant();
			if (!start.isAfter(this.clock.instant())) {
				errors.add(new ApiFieldError("target.date", "in_the_past", "The lecture cannot be moved to a time that has passed."));
			}
		}
		if (target.equals(lecture.slot())) {
			errors.add(new ApiFieldError("target", "no_change", "The lecture is already there."));
		}
	}

	private static void requireValid(List<ApiFieldError> errors) {
		if (!errors.isEmpty()) {
			throw invalid(errors);
		}
	}

	private static ApiException invalid(List<ApiFieldError> errors) {
		return ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
	}

	private static String words(TicketStatus status) {
		return status.name().toLowerCase().replace('_', ' ');
	}

	private static String clean(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static ApiException lectureNotFound() {
		return ApiException.notFound(LECTURE_NOT_FOUND, "There is no lecture with that ID.");
	}

	private static ApiException lectureGone() {
		return ApiException.conflict(LECTURE_NOT_FOUND, "The lecture this ticket concerns no longer exists.");
	}

}
