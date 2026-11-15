package com.kibootia.backend.rescheduling.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.rescheduling.application.Progress.Outcome;
import com.kibootia.backend.rescheduling.TicketChanged;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.ChangeOperation;
import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;
import com.kibootia.backend.timetable.ChangeConflictException;
import com.kibootia.backend.timetable.LectureCalendar;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureView;

/**
 * Applies a ticket: the one place where a ticket's change is made to the live timetable, whether the author applies their own
 * change or the last reviewer's approval completes a review. Package-private: who may apply is decided by the use case that
 * calls this, which has asked the policy; this makes the change and keeps the records.
 *
 * <p>Before anything is changed it looks at the time. A ticket whose lecture has started expires instead. The conflicts are
 * checked again with the lectures locked, because something may have been done since the ticket was checked; when something
 * is in the way, nothing is changed, what was found is recorded and the ticket is flagged, and that is returned and not
 * thrown so that it is committed.
 *
 * <p>Once a change is made it may free a slot or take one that a ticket under review was waiting for, so the tickets under
 * review that it could affect are checked again, and flagged or cleared.
 */
@Component
class TicketApplier {

	private final TicketStore tickets;

	private final LectureCalendar calendar;

	private final TicketSupport support;

	private final AuditLog auditLog;

	private final Clock clock;

	private final TicketNotices notices;

	TicketApplier(TicketStore tickets, LectureCalendar calendar, TicketSupport support, AuditLog auditLog, Clock clock,
			TicketNotices notices) {
		this.notices = notices;
		this.tickets = tickets;
		this.calendar = calendar;
		this.support = support;
		this.auditLog = auditLog;
		this.clock = clock;
	}

	/** Whether the ticket's lecture has started, and so it can no longer be applied. */
	boolean due(Ticket ticket) {
		return !this.clock.instant().isBefore(ticket.expiresAt());
	}

	/** Expires the ticket, which has not been applied by the time its earliest lecture starts. */
	Progress expire(Ticket ticket) {
		Ticket expired = this.tickets.update(ticket.expired(this.clock.instant()));
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.expired", "ticket", ticket.id())
			.withChange("from", ticket.status().name())
			.withChange("to", expired.status().name()));
		return new Progress(Outcome.EXPIRED, this.support.detail(expired));
	}

	Progress applyNow(Ticket ticket, ApprovalPath path) {
		if (due(ticket)) {
			return expire(ticket);
		}
		Instant now = this.clock.instant();
		List<TicketChange> changes = this.tickets.changesOf(ticket.id());
		// An extra lecture does not exist before the ticket is applied, so it has no earlier state to keep.
		Map<UUID, LectureView> before = new HashMap<>();
		for (TicketChange change : changes) {
			if (change.sourceOccurrenceId() != null) {
				this.calendar.lecture(change.sourceOccurrenceId()).ifPresent((lecture) -> before.put(change.id(), lecture));
			}
		}
		List<UUID> made;
		try {
			made = this.calendar.apply(this.support.toLectureChanges(changes), ticket.id());
		}
		catch (ChangeConflictException ex) {
			this.tickets.recordCheck(ticket.id(), CheckTrigger.APPLY, ex.conflicts());
			Ticket flagged = this.tickets.update(ticket.conflicted(true, now));
			this.auditLog.record(AuditEntry.of("rescheduling.ticket.blocked", "ticket", ticket.id()));
			if (!ticket.conflicted()) {
				this.notices.publish(TicketChanged.Kind.CONFLICTED, flagged);
			}
			return new Progress(Outcome.BLOCKED, this.support.detail(flagged));
		}
		for (int i = 0; i < changes.size(); i++) {
			TicketChange change = changes.get(i);
			LectureView lecture = before.get(change.id());
			if (lecture != null) {
				this.tickets.recordBefore(change.id(), lecture.slot(), lecture.facultyId());
			}
			if (change.operation() == ChangeOperation.CREATE_EXTRA) {
				this.tickets.recordCreated(change.id(), made.get(i));
			}
		}
		this.tickets.recordCheck(ticket.id(), CheckTrigger.APPLY, List.of());
		Ticket applied = this.tickets.update(ticket.applied(path, now));
		if (ticket.type() != TicketType.REVERT) {
			this.notices.publish(TicketChanged.Kind.APPLIED, applied);
		}
		if (ticket.type() == TicketType.REVERT) {
			this.tickets.find(ticket.revertsTicketId()).ifPresent((original) -> {
				this.tickets.update(original.reverted(ticket.id()));
				// Everyone who was told of the original change is told it was undone.
				this.notices.publish(TicketChanged.Kind.REVERTED, original);
				this.auditLog.record(AuditEntry.of("rescheduling.ticket.reverted", "ticket", original.id())
					.withChange("from", original.status().name())
					.withChange("to", TicketStatus.REVERTED.name())
					.withChange("by", ticket.id().toString()));
			});
		}
		this.auditLog.record(AuditEntry.of("rescheduling.ticket.applied", "ticket", ticket.id())
			.withChange("from", ticket.status().name())
			.withChange("to", applied.status().name())
			.withChange("path", path.name()));
		recheckTicketsUnderReview(applied, changes, before.values());
		return new Progress(Outcome.APPLIED, this.support.detail(applied));
	}

	/**
	 * A change moves a lecture off one date and onto another, so a ticket under review that wants a slot on either date, or is
	 * about the same lecture, may now be in the way, or no longer be. Each is checked again; one that is blocked is flagged, and
	 * one that was flagged and is clear has the flag removed. The flag is the sign to the author and the HOD; applying stays
	 * blocked while it is up.
	 */
	private void recheckTicketsUnderReview(Ticket applied, List<TicketChange> changes, Collection<LectureView> before) {
		Set<LocalDate> dates = new HashSet<>();
		Set<UUID> lectures = new HashSet<>();
		for (TicketChange change : changes) {
			if (change.target() != null) {
				dates.add(change.target().date());
			}
			if (change.occurrenceId() != null) {
				lectures.add(change.occurrenceId());
			}
		}
		before.forEach((lecture) -> dates.add(lecture.slot().date()));
		Instant now = this.clock.instant();
		for (Ticket other : this.tickets.inReviewAffectedBy(lectures, dates)) {
			if (other.id().equals(applied.id())) {
				continue;
			}
			List<LectureConflict> conflicts = this.calendar.check(this.support.toLectureChanges(this.tickets.changesOf(other.id())));
			boolean blocked = !conflicts.isEmpty();
			if (blocked || other.conflicted()) {
				this.tickets.recordCheck(other.id(), CheckTrigger.RECHECK, conflicts);
			}
			if (blocked != other.conflicted()) {
				Ticket updated = this.tickets.update(other.conflicted(blocked, now));
				if (blocked) {
					this.notices.publish(TicketChanged.Kind.CONFLICTED, updated);
				}
				this.auditLog.record(AuditEntry.of(blocked ? "rescheduling.ticket.conflicted" : "rescheduling.ticket.conflict-cleared",
						"ticket", other.id()));
			}
		}
	}

}
