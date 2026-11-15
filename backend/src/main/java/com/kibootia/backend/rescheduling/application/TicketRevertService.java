package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.academic.AcademicPermissions;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureSlot;
import com.kibootia.backend.timetable.LectureView;

/**
 * Reverting an applied ticket (rules: reverts). A revert is a new ticket that restores each lecture the applied ticket
 * changed to where, and with whom, it was; the applied ticket shows as reverted, linked to it, once the revert is applied.
 *
 * <p>Who may revert: the author of the change, the faculty members it affects (the teacher whose lecture was moved, the
 * substitute, the other teacher of an exchange) and the HOD of its place, each until the lecture starts. The revert is
 * raised as a draft and checked like any change; if the original slot has been taken since, it is blocked until that is
 * resolved. It is applied, or sent for review, like any other ticket.
 *
 * <p>Only the latest change to a lecture can be reverted: when another ticket has changed the lecture since, undoing this
 * one would undo that too. An extra lecture is not reverted; it is cancelled.
 */
@Service
public class TicketRevertService {

	public static final String NOT_APPLIED = "rescheduling.ticket_not_applied";

	public static final String NOT_REVERTIBLE = "rescheduling.not_revertible";

	public static final String REVERT_EXISTS = "rescheduling.revert_exists";

	public static final String CHANGED_SINCE = "rescheduling.lecture_changed_since";

	public static final String ORIGINAL_SLOT_PASSED = "rescheduling.original_slot_passed";

	private static final Set<TicketType> REVERTIBLE = Set.of(TicketType.RESCHEDULE, TicketType.CANCEL, TicketType.SUBSTITUTE,
			TicketType.EXCHANGE, TicketType.PERMANENT_CHANGE);

	private final TicketStore tickets;

	private final LectureCalendar calendar;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final Clock clock;

	private final TicketSupport support;

	TicketRevertService(TicketStore tickets, LectureCalendar calendar, Authorization authorization, AuditLog auditLog,
			Clock clock, TicketSupport support) {
		this.tickets = tickets;
		this.calendar = calendar;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.clock = clock;
		this.support = support;
	}

	/**
	 * Raises, as a draft, the ticket that reverts this applied one, and checks it. A ticket the caller may not see is not
	 * found; one they see but may not revert is refused.
	 *
	 * @param reason why, or null
	 */
	@Transactional
	public TicketDetail revert(UUID id, String reason) {
		UUID me = this.authorization.currentUserId();
		Ticket original = this.support.load(id);
		TicketFacts facts = this.support.facts(original);
		this.support.requireSeen(facts);
		if (!this.authorization.can(AcademicPermissions.TICKETS_REVERT, facts)) {
			this.authorization.require(AcademicPermissions.TICKETS_REVERT_IN_SCOPE, facts);
		}
		if (original.status() != TicketStatus.APPLIED) {
			throw ApiException.conflict(NOT_APPLIED, "Only a ticket that has been applied can be reverted.");
		}
		if (!REVERTIBLE.contains(original.type())) {
			throw ApiException.conflict(NOT_REVERTIBLE,
					"A ticket of this kind cannot be reverted. To undo an extra lecture, cancel it.");
		}
		if (this.tickets.hasOpenRevert(id)) {
			throw ApiException.conflict(REVERT_EXISTS, "A revert of this ticket has already been raised.");
		}

		Instant now = this.clock.instant();
		List<NewTicket.Change> restores = new ArrayList<>();
		List<LectureView> lectures = new ArrayList<>();
		Instant expires = null;
		for (TicketChange applied : this.tickets.changesOf(id)) {
			LectureView lecture = this.calendar.lecture(applied.sourceOccurrenceId())
				.orElseThrow(() -> ApiException.conflict(TicketService.LECTURE_NOT_FOUND,
						"A lecture this ticket changed no longer exists."));
			if (!id.equals(lecture.changedByTicketId())) {
				throw ApiException.conflict(CHANGED_SINCE,
						"Another ticket has changed the lecture since, so this one can no longer be reverted on its own.");
			}
			if (!lecture.startsAt().isAfter(now)) {
				throw ApiException.conflict(TicketService.LECTURE_STARTED, "The lecture has started, so it can no longer be changed.");
			}
			LectureSlot back = applied.before();
			Instant backStart = LocalDateTime.of(back.date(), back.startTime()).atZone(this.calendar.zone()).toInstant();
			if (!backStart.isAfter(now)) {
				throw ApiException.conflict(ORIGINAL_SLOT_PASSED, "The slot the lecture came from has passed, so it cannot go back.");
			}
			restores.add(new NewTicket.Change(ChangeOperation.RESTORE, lecture.id(), lecture.revision(), back, clean(reason),
					applied.beforeFacultyId(), null, lecture.facultyId()));
			lectures.add(lecture);
			Instant first = lecture.startsAt().isBefore(backStart) ? lecture.startsAt() : backStart;
			expires = (expires == null || first.isBefore(expires)) ? first : expires;
		}

		LectureView lead = lectures.get(0);
		LectureRef ref = new LectureRef(lead.assignmentId(), lead.departmentId(), lead.divisionId(), lead.batchId(),
				lead.sessionType(), lead.facultyId());
		boolean teaches = lead.teachingFacultyUserIds().contains(me);
		Ticket ticket = this.tickets.insert(new NewTicket(TicketType.REVERT, me, teaches ? null : lead.facultyId(), null, ref,
				expires, restores, id));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.created", "ticket", ticket.id())
			.withChange("type", TicketType.REVERT.name())
			.withChange("reverts", id.toString()));
		return this.support.checked(ticket, CheckTrigger.SUBMISSION);
	}

	private static String clean(String text) {
		return (text == null || text.isBlank()) ? null : text.strip();
	}

}
