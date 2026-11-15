package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
import com.kibootia.backend.reference.AssignmentDetails;
import com.kibootia.backend.rescheduling.application.TicketQuery.Visibility;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.ChangeConflictException;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureChange;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureMode;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;

/**
 * The use cases of tickets that reschedule or cancel one lecture, applied by their author (self-approval). A ticket is a
 * proposal: it is checked when it is raised and again when it is applied, and it changes nothing until then. Who may do what
 * is decided by the policy of the {@code academic} module from the lecture and the people on the ticket; the rules of state,
 * whether the lecture has started or the ticket is still a draft, are this class's.
 *
 * <p>What the policy decides here:
 * <ul>
 * <li>raising a ticket: the teacher of the lecture, or the HOD of its department acting for them
 * ({@code create} and {@code create-on-behalf}, or {@code cancel} and {@code cancel-on-behalf} for a cancellation);</li>
 * <li>applying it: only its author. A reschedule needs {@code self-approve}, held by a faculty member who teaches the
 * lecture; an HOD does not hold it. A cancellation is applied directly by whoever may cancel the lecture;</li>
 * <li>editing and withdrawing it: its author ({@code edit-own});</li>
 * <li>seeing it: the people on it, and the roles whose place covers the lecture.</li>
 * </ul>
 * A ticket the caller may not see is answered as not found.
 *
 * <p>An apply that is refused for conflicts is returned, not thrown, so that the conflicts found are recorded and the ticket
 * is flagged in the same transaction; the web layer then answers 409.
 */
@Service
public class TicketService {

	public static final String TICKET_NOT_FOUND = "rescheduling.ticket_not_found";

	public static final String LECTURE_NOT_FOUND = "rescheduling.lecture_not_found";

	public static final String LECTURE_STARTED = "rescheduling.lecture_started";

	public static final String LECTURE_CANCELLED = "rescheduling.lecture_cancelled";

	public static final String NOT_A_DRAFT = "rescheduling.ticket_not_draft";

	public static final String STALE_TICKET = "rescheduling.stale_ticket";

	private final TicketStore tickets;

	private final LectureCalendar calendar;

	private final AcademicDirectory directory;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final Clock clock;

	TicketService(TicketStore tickets, LectureCalendar calendar, AcademicDirectory directory, Authorization authorization,
			AuditLog auditLog, Clock clock) {
		this.tickets = tickets;
		this.calendar = calendar;
		this.directory = directory;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/**
	 * What a person asks for when raising a ticket.
	 *
	 * @param target where a reschedule puts the lecture; null for a cancellation
	 * @param reason why a lecture is cancelled; required for a cancellation
	 */
	public record Draft(TicketType type, UUID lectureId, String description, LectureSlot target, String reason) {

	}

	/** What the author changes when editing a draft. {@code expectedVersion} is the version of the ticket they last read. */
	public record Edit(String description, LectureSlot target, String reason, long expectedVersion) {

	}

	/** Which tickets a list asks for. {@code mine} narrows it to the tickets the caller wrote. */
	public record TicketFilter(TicketStatus status, UUID lectureId, boolean mine) {

	}

	/** A ticket with the change it makes and what the latest check found. A list leaves {@code conflicts} empty. */
	public record TicketDetail(Ticket ticket, TicketChange change, List<LectureConflict> conflicts) {

	}

	public enum Outcome {

		APPLIED, BLOCKED, EXPIRED

	}

	/** What happened when a ticket was applied; {@code detail} is the ticket as it is now, whichever the outcome. */
	public record Applied(Outcome outcome, TicketDetail detail) {

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
		requireChangeable(lecture);
		NewTicket.Change change = validated(draft.type(), lecture, draft.target(), draft.reason());
		LectureRef ref = new LectureRef(lecture.assignmentId(), lecture.departmentId(), lecture.divisionId(),
				lecture.batchId(), lecture.sessionType(), lecture.facultyId());
		Ticket ticket = this.tickets.insert(new NewTicket(draft.type(), me, teaches ? null : lecture.facultyId(),
				clean(draft.description()), ref, expiresAt(lecture, change.target()), change));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", draft.type().name()));
		return checked(ticket, CheckTrigger.SUBMISSION);
	}

	@Transactional
	public TicketDetail edit(UUID id, Edit edit) {
		Ticket ticket = load(id);
		TicketFacts facts = facts(ticket);
		requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		requireDraft(ticket);
		if (ticket.version() != edit.expectedVersion()) {
			throw ApiException.conflict(STALE_TICKET, "The ticket was changed after you read it. Read it again.");
		}
		TicketChange current = this.tickets.changeOf(id);
		LectureView lecture = this.calendar.lecture(current.sourceOccurrenceId()).orElseThrow(TicketService::lectureGone);
		requireChangeable(lecture);
		// The plan is made against the lecture as it is now, so an edit also brings a stale ticket up to date.
		NewTicket.Change change = validated(ticket.type(), lecture, edit.target(), edit.reason());
		this.tickets.replaceChange(id, change);
		Ticket edited = this.tickets.update(ticket.edited(clean(edit.description()), expiresAt(lecture, change.target())));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.edited", "ticket", id));
		return checked(edited, CheckTrigger.SUBMISSION);
	}

	// --- applying ---------------------------------------------------------------------------------------------------

	/**
	 * Applies the ticket by its author, without a review. The conflicts are checked again with the lecture locked, because
	 * something may have been done since the ticket was raised; the change is made only if nothing is in the way.
	 */
	@Transactional
	public Applied apply(UUID id) {
		Ticket ticket = load(id);
		TicketFacts facts = facts(ticket);
		requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		if (ticket.type() == TicketType.CANCEL) {
			if (!this.authorization.can(AcademicPermissions.TICKETS_CANCEL, facts)) {
				this.authorization.require(AcademicPermissions.TICKETS_CANCEL_ON_BEHALF, facts);
			}
		}
		else {
			this.authorization.require(AcademicPermissions.TICKETS_SELF_APPROVE, facts);
		}
		requireDraft(ticket);

		Instant now = this.clock.instant();
		if (!now.isBefore(ticket.expiresAt())) {
			Ticket expired = this.tickets.update(ticket.expired(now));
			this.auditLog.record(AuditEntry.of("rescheduling.ticket.expired", "ticket", id)
				.withChange("from", ticket.status().name())
				.withChange("to", expired.status().name()));
			return new Applied(Outcome.EXPIRED, detail(expired));
		}

		TicketChange change = this.tickets.changeOf(id);
		Optional<LectureView> lecture = this.calendar.lecture(change.sourceOccurrenceId());
		try {
			this.calendar.apply(List.of(toLectureChange(change)), ticket.id());
		}
		catch (ChangeConflictException ex) {
			this.tickets.recordCheck(id, CheckTrigger.APPLY, ex.conflicts());
			Ticket flagged = this.tickets.update(ticket.conflicted(true, now));
			this.auditLog.record(AuditEntry.of("rescheduling.ticket.blocked", "ticket", id));
			return new Applied(Outcome.BLOCKED, new TicketDetail(flagged, change, ex.conflicts()));
		}
		lecture.ifPresent((before) -> this.tickets.recordBefore(id, before.slot()));
		this.tickets.recordCheck(id, CheckTrigger.APPLY, List.of());
		Ticket applied = this.tickets.update(ticket.applied(ApprovalPath.SELF, now));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.applied", "ticket", id)
			.withChange("from", ticket.status().name())
			.withChange("to", applied.status().name())
			.withChange("path", ApprovalPath.SELF.name()));
		return new Applied(Outcome.APPLIED, detail(applied));
	}

	@Transactional
	public TicketDetail withdraw(UUID id) {
		Ticket ticket = load(id);
		TicketFacts facts = facts(ticket);
		requireSeen(facts);
		this.authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts);
		if (!ticket.status().canBecome(TicketStatus.WITHDRAWN)) {
			throw ApiException.conflict(NOT_A_DRAFT, "A ticket that is " + ticket.status().name().toLowerCase().replace('_', ' ')
					+ " cannot be withdrawn.");
		}
		Ticket withdrawn = this.tickets.update(ticket.withdrawn(this.clock.instant()));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.withdrawn", "ticket", id)
			.withChange("from", ticket.status().name())
			.withChange("to", withdrawn.status().name()));
		return detail(withdrawn);
	}

	// --- reading ----------------------------------------------------------------------------------------------------

	@Transactional(readOnly = true)
	public TicketDetail get(UUID id) {
		Ticket ticket = load(id);
		TicketFacts facts = facts(ticket);
		if (!this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts)) {
			throw ticketNotFound();
		}
		return detail(ticket);
	}

	/**
	 * The tickets the caller may see: those they are on (as the author, or as the teacher of the lecture), and those whose
	 * lecture their role covers. Filtered in the query, from {@link Authorization#reach}, before paging.
	 */
	@Transactional(readOnly = true)
	public Page<TicketDetail> list(TicketFilter filter, Pageable pageable) {
		UUID me = this.authorization.currentUserId();
		Reach inScope = this.authorization.reach(AcademicPermissions.TICKETS_VIEW_IN_SCOPE);
		boolean onOwn = !this.authorization.reach(AcademicPermissions.TICKETS_VIEW).isEmpty();
		Visibility visibility = new Visibility(inScope.everywhere(), inScope.idsOf(AcademicScopes.DEPARTMENT),
				inScope.idsOf(AcademicScopes.DIVISION), inScope.idsOf(AcademicScopes.BATCH), onOwn ? me : null,
				onOwn ? this.directory.facultyIdsOf(me) : Set.of());
		if (visibility.isEmpty()) {
			return Page.empty(pageable);
		}
		Page<Ticket> page = this.tickets.search(
				new TicketQuery(filter.status(), filter.lectureId(), filter.mine() ? me : null, visibility), pageable);
		Map<UUID, TicketChange> changes = this.tickets
			.changesOf(page.getContent().stream().map(Ticket::id).collect(Collectors.toList()));
		return page.map((ticket) -> new TicketDetail(ticket, changes.get(ticket.id()), List.of()));
	}

	// --- helpers ----------------------------------------------------------------------------------------------------

	private Ticket load(UUID id) {
		return this.tickets.find(id).orElseThrow(TicketService::ticketNotFound);
	}

	/** A ticket the caller may not see is not found, whatever they ask to do with it. */
	private void requireSeen(TicketFacts facts) {
		if (!this.authorization.can(AcademicPermissions.TICKETS_VIEW, facts)
				&& !this.authorization.can(AcademicPermissions.TICKETS_VIEW_IN_SCOPE, facts)) {
			throw ticketNotFound();
		}
	}

	private TicketFacts facts(Ticket ticket) {
		UUID assignmentId = ticket.lecture().assignmentId();
		AssignmentDetails assignment = this.directory.assignments(Set.of(assignmentId)).get(assignmentId);
		return TicketFacts.of(ticket, assignment);
	}

	private boolean mayViewLecture(LectureView lecture) {
		return this.authorization.can(AcademicPermissions.LIVE_VIEW_ALL)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW, lecture)
				|| this.authorization.can(AcademicPermissions.LIVE_VIEW_OWN, lecture);
	}

	private TicketDetail detail(Ticket ticket) {
		return new TicketDetail(ticket, this.tickets.changeOf(ticket.id()), this.tickets.latestCheck(ticket.id()));
	}

	/** Checks the ticket's conflicts, records the result, and flags the ticket when something is in the way. */
	private TicketDetail checked(Ticket ticket, CheckTrigger trigger) {
		TicketChange change = this.tickets.changeOf(ticket.id());
		List<LectureConflict> conflicts = this.calendar.check(List.of(toLectureChange(change)));
		this.tickets.recordCheck(ticket.id(), trigger, conflicts);
		Ticket flagged = ticket.conflicted(!conflicts.isEmpty(), this.clock.instant());
		Ticket saved = (flagged.conflicted() == ticket.conflicted()) ? ticket : this.tickets.update(flagged);
		return new TicketDetail(saved, change, conflicts);
	}

	private static LectureChange toLectureChange(TicketChange change) {
		return (change.operation() == ChangeOperation.CANCEL)
				? LectureChange.cancel(change.sourceOccurrenceId(), change.sourceRevision())
				: LectureChange.move(change.sourceOccurrenceId(), change.sourceRevision(), change.target());
	}

	/** A lecture that is cancelled or has started cannot be changed. */
	private void requireChangeable(LectureView lecture) {
		if (lecture.cancelled()) {
			throw ApiException.conflict(LECTURE_CANCELLED, "The lecture is cancelled.");
		}
		if (!lecture.startsAt().isAfter(this.clock.instant())) {
			throw ApiException.conflict(LECTURE_STARTED, "The lecture has started, so it can no longer be changed.");
		}
	}

	private static void requireDraft(Ticket ticket) {
		if (ticket.status() != TicketStatus.DRAFT) {
			throw ApiException.conflict(NOT_A_DRAFT, "The ticket is " + ticket.status().name().toLowerCase().replace('_', ' ')
					+ ", not a draft.");
		}
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
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
	}

	private static String clean(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

	private static ApiException ticketNotFound() {
		return ApiException.notFound(TICKET_NOT_FOUND, "There is no ticket with that ID.");
	}

	private static ApiException lectureNotFound() {
		return ApiException.notFound(LECTURE_NOT_FOUND, "There is no lecture with that ID.");
	}

	private static ApiException lectureGone() {
		return ApiException.conflict(LECTURE_NOT_FOUND, "The lecture this ticket concerns no longer exists.");
	}

}
