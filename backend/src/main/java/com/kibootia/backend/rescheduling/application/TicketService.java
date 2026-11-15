package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
import com.kibootia.backend.platform.tenancy.TenantContext;
import com.kibootia.backend.reference.AcademicDirectory;
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.rescheduling.application.TicketQuery.Visibility;
import com.kibootia.backend.rescheduling.TicketChanged;
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
 * The use cases of tickets that reschedule, cancel or find a substitute for one lecture: raising and editing them, applying them, withdrawing them,
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

	public static final String ASSIGNMENT_NOT_FOUND = "rescheduling.assignment_not_found";

	public static final String NEEDS_HOD_APPROVAL = "rescheduling.needs_hod_approval";

	public static final String NOT_WEEKLY = "rescheduling.not_weekly";

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

	private final TicketNotices notices;

	TicketService(TicketStore tickets, LectureCalendar calendar, AcademicDirectory directory, Authorization authorization,
			AuditLog auditLog, Clock clock, TicketSupport support, TicketApplier applier, TicketNotices notices) {
		this.notices = notices;
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
	 * @param lectureId the lecture to change; every type but an extra lecture
	 * @param otherLectureId the lecture to exchange places with; an exchange only
	 * @param assignmentId the teaching assignment to add a lecture of; an extra lecture only
	 * @param target where a reschedule puts the lecture, or where an extra lecture goes; null for a cancellation or a substitute
	 * @param reason why a lecture is cancelled; required for a cancellation
	 * @param substituteFacultyId who takes the lecture; required for a substitute and not allowed otherwise
	 */
	public record Draft(TicketType type, UUID lectureId, UUID otherLectureId, UUID assignmentId, String description,
			LectureSlot target, String reason, UUID substituteFacultyId) {

	}

	/**
	 * What the author changes when editing. {@code expectedVersion} is the version of the ticket they last read.
	 *
	 * @param reviewers for a ticket under review, the people to ask from now on; null leaves them as they are
	 */
	public record Edit(String description, LectureSlot target, String reason, UUID substituteFacultyId,
			List<ReviewerRequest> reviewers, long expectedVersion) {

	}

	/** Which tickets a list asks for. {@code mine} narrows it to the tickets the caller wrote. */
	public record TicketFilter(TicketStatus status, UUID lectureId, boolean mine) {

	}

	// --- raising and editing ----------------------------------------------------------------------------------------

	@Transactional
	public TicketDetail create(Draft draft) {
		UUID me = this.authorization.currentUserId();
		if (draft.type() == TicketType.EXTRA) {
			return createExtra(draft, me);
		}
		if (draft.type() == TicketType.EXCHANGE) {
			return createExchange(draft, me);
		}
		if (draft.type() == TicketType.PERMANENT_CHANGE) {
			return createPermanent(draft, me);
		}
		List<ApiFieldError> named = new ArrayList<>();
		if (draft.otherLectureId() != null) {
			named.add(new ApiFieldError("otherLectureId", "not_allowed", "Only an exchange names a second lecture."));
		}
		if (draft.lectureId() == null) {
			named.add(new ApiFieldError("lectureId", "required", "A ticket names the lecture it changes."));
		}
		if (draft.assignmentId() != null) {
			named.add(new ApiFieldError("assignmentId", "not_allowed", "Only an extra lecture names a teaching assignment."));
		}
		requireValid(named);
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
		NewTicket.Change change = validated(draft.type(), lecture, draft.target(), draft.reason(), draft.substituteFacultyId());
		LectureRef ref = new LectureRef(lecture.assignmentId(), lecture.departmentId(), lecture.divisionId(),
				lecture.batchId(), lecture.sessionType(), lecture.facultyId());
		Ticket ticket = this.tickets.insert(new NewTicket(draft.type(), me, teaches ? null : lecture.facultyId(),
				clean(draft.description()), ref, expiresAt(lecture, change.target()), List.of(change)));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", draft.type().name()));
		return checked(ticket, CheckTrigger.SUBMISSION);
	}

	/**
	 * An extra lecture: there is no lecture to look at, so the teaching assignment stands in for it. The teacher of the
	 * assignment may add a lecture of it, and so may the HOD of its department for them; anyone else is told there is no
	 * such assignment.
	 */
	private TicketDetail createExtra(Draft draft, UUID me) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (draft.assignmentId() == null) {
			errors.add(new ApiFieldError("assignmentId", "required", "An extra lecture names the teaching assignment."));
		}
		if (draft.lectureId() != null) {
			errors.add(new ApiFieldError("lectureId", "not_allowed", "An extra lecture is not a change to a lecture."));
		}
		requireValid(errors);
		AssignmentDetails assignment = this.directory.assignments(Set.of(draft.assignmentId())).get(draft.assignmentId());
		if (assignment == null) {
			throw assignmentNotFound();
		}
		AssignmentFacts facts = AssignmentFacts.of(TenantContext.require(), assignment);
		boolean teaches = this.authorization.can(AcademicPermissions.TICKETS_CREATE, facts);
		if (!teaches && !this.authorization.can(AcademicPermissions.TICKETS_CREATE_ON_BEHALF, facts)) {
			throw assignmentNotFound();
		}
		NewTicket.Change change = validatedExtra(assignment.id(), assignment.facultyId(), draft.target(), draft.reason(),
				draft.substituteFacultyId());
		LectureRef ref = new LectureRef(assignment.id(), assignment.departmentId(), assignment.divisionId(),
				assignment.batchId(), assignment.sessionType(), assignment.facultyId());
		Ticket ticket = this.tickets.insert(new NewTicket(TicketType.EXTRA, me, teaches ? null : assignment.facultyId(),
				clean(draft.description()), ref, expiresAt(null, change.target()), List.of(change)));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", TicketType.EXTRA.name()));
		return checked(ticket, CheckTrigger.SUBMISSION);
	}

	private NewTicket.Change validatedExtra(UUID assignmentId, UUID facultyId, LectureSlot target, String reason,
			UUID substituteFacultyId) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (substituteFacultyId != null) {
			errors.add(new ApiFieldError("substituteFacultyId", "not_allowed", "Only a substitute names who takes the lecture."));
		}
		if (target == null) {
			errors.add(new ApiFieldError("target", "required", "An extra lecture says when and where it is held."));
		}
		else {
			validateTarget(null, target, errors);
		}
		requireValid(errors);
		return new NewTicket.Change(ChangeOperation.CREATE_EXTRA, null, 0, target, clean(reason), null, assignmentId, facultyId);
	}

	/**
	 * A permanent change: a lecture of the weekly timetable moves every week from its date onward. It is made as one move
	 * for each lecture the weekly entry still has from then on, so it applies all together or not at all, and the one raised
	 * on is shifted by the same number of days as the first. A lecture that is cancelled, already changed by another ticket
	 * or already started is left as it is. It can never be applied by its author alone; an HOD must approve it.
	 */
	private TicketDetail createPermanent(Draft draft, UUID me) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (draft.lectureId() == null) {
			errors.add(new ApiFieldError("lectureId", "required", "A permanent change names a lecture of the weekly timetable."));
		}
		if (draft.otherLectureId() != null) {
			errors.add(new ApiFieldError("otherLectureId", "not_allowed", "Only an exchange names a second lecture."));
		}
		if (draft.assignmentId() != null) {
			errors.add(new ApiFieldError("assignmentId", "not_allowed", "Only an extra lecture names a teaching assignment."));
		}
		requireValid(errors);
		LectureView lecture = this.calendar.lecture(draft.lectureId()).orElseThrow(TicketService::lectureNotFound);
		if (!mayViewLecture(lecture)) {
			throw lectureNotFound();
		}
		boolean teaches = this.authorization.can(AcademicPermissions.TICKETS_CREATE, lecture);
		if (!teaches) {
			this.authorization.require(AcademicPermissions.TICKETS_CREATE_ON_BEHALF, lecture);
		}
		this.support.requireChangeable(lecture);
		List<LectureView> series = this.calendar.series(lecture.id());
		if (series.isEmpty()) {
			throw ApiException.conflict(NOT_WEEKLY, "Only a lecture of the weekly timetable can be changed permanently.");
		}
		// The lecture raised on is checked as any move is; the rest of the series follow it by the same number of days.
		NewTicket.Change first = validated(TicketType.RESCHEDULE, lecture, draft.target(), draft.reason(), null);
		LectureSlot to = draft.target();
		long shift = ChronoUnit.DAYS.between(lecture.slot().date(), to.date());
		Instant now = this.clock.instant();
		List<NewTicket.Change> changes = new ArrayList<>();
		changes.add(first);
		for (LectureView later : series) {
			if (later.id().equals(lecture.id()) || later.cancelled() || later.changed() || !later.startsAt().isAfter(now)) {
				continue;
			}
			LectureSlot there = new LectureSlot(later.slot().date().plusDays(shift), to.startTime(), to.endTime(), to.mode(),
					to.roomId(), to.meetingLink());
			changes.add(new NewTicket.Change(ChangeOperation.MOVE, later.id(), later.revision(), there, null, null, null,
					later.facultyId()));
		}
		LectureRef ref = new LectureRef(lecture.assignmentId(), lecture.departmentId(), lecture.divisionId(), lecture.batchId(),
				lecture.sessionType(), lecture.facultyId());
		Ticket ticket = this.tickets.insert(new NewTicket(TicketType.PERMANENT_CHANGE, me, teaches ? null : lecture.facultyId(),
				clean(draft.description()), ref, expiresAt(lecture, first.target()), changes));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", TicketType.PERMANENT_CHANGE.name())
			.withChange("lectures", String.valueOf(changes.size())));
		return checked(ticket, CheckTrigger.SUBMISSION);
	}

	/**
	 * An exchange: two lectures of one division take each other's place, and both moves are made or neither. The author
	 * teaches the first lecture, or is the HOD acting for its teacher. The second is a lecture of the same division that the
	 * author may see (a faculty member sees the whole timetable of a division they teach in); any other is answered as not
	 * found. Asking the other teacher for consent is optional, so it is only a review the author may request.
	 */
	private TicketDetail createExchange(Draft draft, UUID me) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (draft.lectureId() == null) {
			errors.add(new ApiFieldError("lectureId", "required", "An exchange names the author's lecture."));
		}
		if (draft.otherLectureId() == null) {
			errors.add(new ApiFieldError("otherLectureId", "required", "An exchange names the lecture to exchange with."));
		}
		else if (draft.otherLectureId().equals(draft.lectureId())) {
			errors.add(new ApiFieldError("otherLectureId", "same_lecture", "A lecture cannot be exchanged with itself."));
		}
		if (draft.assignmentId() != null) {
			errors.add(new ApiFieldError("assignmentId", "not_allowed", "Only an extra lecture names a teaching assignment."));
		}
		if (draft.target() != null) {
			errors.add(new ApiFieldError("target", "not_allowed", "An exchange puts each lecture where the other is."));
		}
		if (draft.substituteFacultyId() != null) {
			errors.add(new ApiFieldError("substituteFacultyId", "not_allowed", "Only a substitute names who takes the lecture."));
		}
		requireValid(errors);
		LectureView mine = this.calendar.lecture(draft.lectureId()).orElseThrow(TicketService::lectureNotFound);
		if (!mayViewLecture(mine)) {
			throw lectureNotFound();
		}
		boolean teaches = this.authorization.can(AcademicPermissions.TICKETS_CREATE, mine);
		if (!teaches) {
			this.authorization.require(AcademicPermissions.TICKETS_CREATE_ON_BEHALF, mine);
		}
		LectureView other = this.calendar.lecture(draft.otherLectureId())
			.filter((lecture) -> lecture.divisionId().equals(mine.divisionId()) && mayViewLecture(lecture))
			.orElseThrow(TicketService::lectureNotFound);
		this.support.requireChangeable(mine);
		this.support.requireChangeable(other);
		if (mine.sessionType() != other.sessionType()) {
			errors.add(new ApiFieldError("otherLectureId", "different_session",
					"Only lectures of the same kind can be exchanged: two theory lectures, or two labs of one batch."));
		}
		else if (!Objects.equals(mine.batchId(), other.batchId())) {
			errors.add(new ApiFieldError("otherLectureId", "different_batch", "The two lectures are for different batches."));
		}
		if (mine.facultyId().equals(other.facultyId())) {
			errors.add(new ApiFieldError("otherLectureId", "same_faculty",
					"The same faculty member teaches both lectures, so nobody changes places."));
		}
		requireValid(errors);
		List<NewTicket.Change> changes = exchangeChanges(mine, other);
		LectureRef ref = new LectureRef(mine.assignmentId(), mine.departmentId(), mine.divisionId(), mine.batchId(),
				mine.sessionType(), mine.facultyId());
		Ticket ticket = this.tickets.insert(new NewTicket(TicketType.EXCHANGE, me, teaches ? null : mine.facultyId(),
				clean(draft.description()), ref, earlier(mine.startsAt(), other.startsAt()), changes));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", TicketType.EXCHANGE.name()));
		return checked(ticket, CheckTrigger.SUBMISSION);
	}

	/**
	 * Each lecture goes to the other's date, start, room and mode, and keeps its own length. The two are checked together by
	 * the timetable, which lets each take the place the other leaves.
	 */
	private static List<NewTicket.Change> exchangeChanges(LectureView mine, LectureView other) {
		return List.of(swap(mine, other), swap(other, mine));
	}

	private static NewTicket.Change swap(LectureView moving, LectureView other) {
		LectureSlot there = other.slot();
		LectureSlot to = new LectureSlot(there.date(), there.startTime(), there.startTime().plusHours(moving.slot().hours()),
				there.mode(), there.roomId(), there.meetingLink());
		if (!to.endTime().isAfter(to.startTime())) {
			throw invalid(List.of(new ApiFieldError("otherLectureId", "too_long",
					"The lecture would run past midnight if it started at " + there.startTime() + ".")));
		}
		return new NewTicket.Change(ChangeOperation.MOVE, moving.id(), moving.revision(), to, null, null, null,
				moving.facultyId());
	}

	/** An exchange has no plan of its own to edit: the places are those of the two lectures. */
	private static void requireNoPlanFields(Edit edit) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (edit.target() != null) {
			errors.add(new ApiFieldError("target", "not_allowed", "An exchange puts each lecture where the other is."));
		}
		if (edit.substituteFacultyId() != null) {
			errors.add(new ApiFieldError("substituteFacultyId", "not_allowed", "Only a substitute names who takes the lecture."));
		}
		requireValid(errors);
	}

	private LectureView lectureOf(TicketChange change) {
		return this.calendar.lecture(change.sourceOccurrenceId()).orElseThrow(TicketService::lectureGone);
	}

	private static Instant earlier(Instant first, Instant second) {
		return first.isBefore(second) ? first : second;
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
		if (ticket.type() == TicketType.REVERT || ticket.type() == TicketType.PERMANENT_CHANGE
				|| !EDITABLE.contains(ticket.status())) {
			throw ApiException.conflict(NOT_EDITABLE, "A ticket that is " + words(ticket.status()) + " cannot be edited.");
		}
		if (ticket.version() != edit.expectedVersion()) {
			throw ApiException.conflict(STALE_TICKET, "The ticket was changed after you read it. Read it again.");
		}
		if (edit.reviewers() != null && ticket.status() != TicketStatus.IN_REVIEW) {
			throw invalid(List.of(new ApiFieldError("reviewers", "not_in_review",
					"The people to ask can be changed only while the ticket is under review.")));
		}
		List<TicketChange> current = this.tickets.changesOf(id);
		List<NewTicket.Change> changes;
		Instant expires;
		if (ticket.type() == TicketType.EXCHANGE) {
			LectureView mine = lectureOf(current.get(0));
			LectureView other = lectureOf(current.get(1));
			requireNoPlanFields(edit);
			this.support.requireChangeable(mine);
			this.support.requireChangeable(other);
			// The places are swapped again as the lectures are now, so an edit also brings a stale ticket up to date.
			changes = exchangeChanges(mine, other);
			expires = earlier(mine.startsAt(), other.startsAt());
		}
		else if (current.get(0).operation() == ChangeOperation.CREATE_EXTRA) {
			NewTicket.Change change = validatedExtra(current.get(0).assignmentId(), current.get(0).facultyId(), edit.target(),
					edit.reason(), edit.substituteFacultyId());
			changes = List.of(change);
			expires = expiresAt(null, change.target());
		}
		else {
			LectureView lecture = lectureOf(current.get(0));
			this.support.requireChangeable(lecture);
			// The plan is made against the lecture as it is now, so an edit also brings a stale ticket up to date.
			NewTicket.Change change = validated(ticket.type(), lecture, edit.target(), edit.reason(),
					edit.substituteFacultyId());
			changes = List.of(change);
			expires = expiresAt(lecture, change.target());
		}
		List<NewReview> reviewers = (edit.reviewers() == null) ? null
				: this.support.validatedReviewers(ticket.authorUserId(), edit.reviewers());

		boolean underReview = ticket.status() == TicketStatus.IN_REVIEW;
		boolean approvalsCleared = underReview && planChanged(current, changes);
		this.tickets.replaceChanges(id, changes);
		Ticket edited = ticket.edited(clean(edit.description()), expires);
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
		if (approvalsCleared) {
			// The people asked are asked again.
			this.notices.publish(TicketChanged.Kind.EDITED, edited);
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
				if (ticket.type() == TicketType.PERMANENT_CHANGE) {
					throw ApiException.conflict(NEEDS_HOD_APPROVAL,
							"A permanent change is never applied by its author alone: send it for review to an HOD.");
				}
				if (ticket.type() == TicketType.REVERT) {
					// A revert is made by the author or an affected faculty member of the change, or the HOD of its place.
					if (!this.authorization.can(AcademicPermissions.TICKETS_REVERT, facts)) {
						this.authorization.require(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, facts);
					}
				}
				else if (ticket.type() == TicketType.CANCEL) {
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
				if (!this.support.approvedToApply(ticket)) {
					throw ApiException.conflict(NEEDS_HOD_APPROVAL, "A permanent change needs the approval of an HOD.");
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
		if (ticket.status() == TicketStatus.IN_REVIEW) {
			this.notices.publish(TicketChanged.Kind.WITHDRAWN, withdrawn);
		}
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
		Map<UUID, List<TicketChange>> changes = this.tickets
			.changesOf(page.getContent().stream().map(Ticket::id).collect(Collectors.toList()));
		return page.map((ticket) -> new TicketDetail(ticket, changes.get(ticket.id()), List.of(), List.of()));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private boolean mayViewLecture(LectureView lecture) {
		return this.authorization.can(AcademicPermissions.LIVE_VIEW_ALL)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW, lecture)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW_DIVISION, lecture)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW_OWN, lecture);
	}

	private Verdict reviewVerdict(Ticket ticket) {
		return Reviews.verdictOf(this.support.currentReviews(ticket).stream().map(TicketReview::status).toList());
	}

	private TicketDetail checked(Ticket ticket, CheckTrigger trigger) {
		return this.support.checked(ticket, trigger);
	}

	/** Whether the edit changes what is proposed: what is done, or where the lecture goes. */
	private static boolean planChanged(List<TicketChange> current, List<NewTicket.Change> changes) {
		if (current.size() != changes.size()) {
			return true;
		}
		for (int i = 0; i < current.size(); i++) {
			if (planChanged(current.get(i), changes.get(i))) {
				return true;
			}
		}
		return false;
	}

	private static boolean planChanged(TicketChange current, NewTicket.Change change) {
		if (current.operation() != change.operation()
				|| !Objects.equals(current.substituteFacultyId(), change.substituteFacultyId())) {
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
		return (lecture == null || targetStart.isBefore(lecture.startsAt())) ? targetStart : lecture.startsAt();
	}

	/** Checks what the person asked for and returns the change it makes; every bad field is reported together. */
	private NewTicket.Change validated(TicketType type, LectureView lecture, LectureSlot target, String reason,
			UUID substituteFacultyId) {
		List<ApiFieldError> errors = new ArrayList<>();
		if (type != TicketType.SUBSTITUTE && substituteFacultyId != null) {
			errors.add(new ApiFieldError("substituteFacultyId", "not_allowed", "Only a substitute names who takes the lecture."));
		}
		if (type == TicketType.SUBSTITUTE) {
			if (substituteFacultyId == null) {
				errors.add(new ApiFieldError("substituteFacultyId", "required", "A substitute says who takes the lecture."));
			}
			else if (substituteFacultyId.equals(lecture.facultyId())) {
				errors.add(new ApiFieldError("substituteFacultyId", "same_faculty",
						"That faculty member already teaches the lecture."));
			}
			else if (!this.directory.faculties(Set.of(substituteFacultyId)).containsKey(substituteFacultyId)) {
				errors.add(new ApiFieldError("substituteFacultyId", "unknown_faculty", "There is no faculty member with that ID."));
			}
			if (target != null) {
				errors.add(new ApiFieldError("target", "not_allowed", "A substitute keeps the lecture where it is."));
			}
			requireValid(errors);
			return new NewTicket.Change(ChangeOperation.REASSIGN_FACULTY, lecture.id(), lecture.revision(), null,
					clean(reason), substituteFacultyId, null, lecture.facultyId());
		}
		if (type == TicketType.CANCEL) {
			if (reason == null || reason.isBlank()) {
				errors.add(new ApiFieldError("reason", "required", "A lecture is cancelled with a reason."));
			}
			if (target != null) {
				errors.add(new ApiFieldError("target", "not_allowed", "A cancellation names no new slot."));
			}
			requireValid(errors);
			return new NewTicket.Change(ChangeOperation.CANCEL, lecture.id(), lecture.revision(), null, reason.strip(), null, null, lecture.facultyId());
		}
		if (target == null) {
			errors.add(new ApiFieldError("target", "required", "A reschedule says where the lecture goes."));
		}
		else {
			validateTarget(lecture, target, errors);
		}
		requireValid(errors);
		return new NewTicket.Change(ChangeOperation.MOVE, lecture.id(), lecture.revision(), target, clean(reason), null, null, lecture.facultyId());
	}

	/** @param lecture the lecture being moved, or null for an extra lecture, which has no length or place to keep */
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
			if (lecture != null && target.endTime().getHour() - target.startTime().getHour() != lecture.slot().hours()) {
				errors.add(new ApiFieldError("target.endTime", "same_length",
						"The lecture lasts " + lecture.slot().hours() + " hour(s), and a move does not change that."));
			}
			Instant start = LocalDateTime.of(target.date(), target.startTime()).atZone(this.calendar.zone()).toInstant();
			if (!start.isAfter(this.clock.instant())) {
				errors.add(new ApiFieldError("target.date", "in_the_past", "The lecture cannot be moved to a time that has passed."));
			}
		}
		if (lecture != null && target.equals(lecture.slot())) {
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

	private static ApiException assignmentNotFound() {
		return ApiException.notFound(ASSIGNMENT_NOT_FOUND, "There is no teaching assignment with that ID.");
	}

	private static ApiException lectureNotFound() {
		return ApiException.notFound(LECTURE_NOT_FOUND, "There is no lecture with that ID.");
	}

	private static ApiException lectureGone() {
		return ApiException.conflict(LECTURE_NOT_FOUND, "The lecture this ticket concerns no longer exists.");
	}

}
