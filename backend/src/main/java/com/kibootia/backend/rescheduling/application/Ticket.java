package com.kibootia.backend.rescheduling.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;

/**
 * A ticket as the use cases see it: plain data with no persistence or HTTP concerns (ADR 0004). A change of state returns a
 * new ticket and refuses a move the lifecycle does not allow, so no use case can put a ticket in a state it cannot be in.
 *
 * @param actingFacultyId the faculty member an HOD acts for, or null when the author is the teacher
 * @param approvalPath how it was applied; null until it is
 * @param revisionNo starts at 1 and rises each time a rejected ticket is edited and sent again
 * @param expiresAt when the earliest lecture it involves starts; a ticket not yet applied lapses then
 * @param conflicted whether the latest check found something in the way
 * @param revertsTicketId the applied ticket this one undoes; set only on a revert
 * @param revertedByTicketId the revert that undid this ticket; set once it has
 * @param closedAt when its own life ended (applied, withdrawn, expired or reverted); null before
 */
public record Ticket(UUID id, UUID tenantId, TicketType type, TicketStatus status, ApprovalPath approvalPath,
		UUID authorUserId, UUID actingFacultyId, String description, int revisionNo, LectureRef lecture,
		Instant expiresAt, boolean conflicted, Instant conflictedSince, Instant closedAt, long version, Instant createdAt,
		Instant updatedAt, UUID revertsTicketId, UUID revertedByTicketId) {

	public Ticket applied(ApprovalPath path, Instant at) {
		requireCanBecome(TicketStatus.APPLIED);
		return new Ticket(this.id, this.tenantId, this.type, TicketStatus.APPLIED, path, this.authorUserId,
				this.actingFacultyId, this.description, this.revisionNo, this.lecture, this.expiresAt, false, null, at,
				this.version, this.createdAt, this.updatedAt, this.revertsTicketId, this.revertedByTicketId);
	}

	/** Sent for review. */
	public Ticket submitted() {
		return moved(TicketStatus.IN_REVIEW, this.revisionNo);
	}

	/** A reviewer rejected it. It is not over: the author can edit and send it again, or take it to the HOD. */
	public Ticket rejected() {
		return moved(TicketStatus.REJECTED, this.revisionNo);
	}

	/** The author edited a rejected ticket: it is a draft again, one revision later. */
	public Ticket resubmitted() {
		return moved(TicketStatus.DRAFT, this.revisionNo + 1);
	}

	/** The author took a rejection to the HOD: back in review at once, one revision later, with the HOD to answer. */
	public Ticket escalated() {
		return moved(TicketStatus.IN_REVIEW, this.revisionNo + 1);
	}

	/** The ticket was undone by an applied revert. */
	public Ticket reverted(UUID byTicketId) {
		requireCanBecome(TicketStatus.REVERTED);
		return new Ticket(this.id, this.tenantId, this.type, TicketStatus.REVERTED, this.approvalPath, this.authorUserId,
				this.actingFacultyId, this.description, this.revisionNo, this.lecture, this.expiresAt, false, null,
				this.closedAt, this.version, this.createdAt, this.updatedAt, this.revertsTicketId, byTicketId);
	}

	public Ticket withdrawn(Instant at) {
		return closed(TicketStatus.WITHDRAWN, at);
	}

	public Ticket expired(Instant at) {
		return closed(TicketStatus.EXPIRED, at);
	}

	/** Records whether the latest check found something in the way, keeping the time it first did. */
	public Ticket conflicted(boolean blocked, Instant at) {
		Instant since = blocked ? ((this.conflictedSince != null) ? this.conflictedSince : at) : null;
		return new Ticket(this.id, this.tenantId, this.type, this.status, this.approvalPath, this.authorUserId,
				this.actingFacultyId, this.description, this.revisionNo, this.lecture, this.expiresAt, blocked, since,
				this.closedAt, this.version, this.createdAt, this.updatedAt, this.revertsTicketId, this.revertedByTicketId);
	}

	/** The ticket after its author edited it: a new description and, since the plan changed, a new expiry. */
	public Ticket edited(String newDescription, Instant newExpiresAt) {
		return new Ticket(this.id, this.tenantId, this.type, this.status, this.approvalPath, this.authorUserId,
				this.actingFacultyId, newDescription, this.revisionNo, this.lecture, newExpiresAt, this.conflicted,
				this.conflictedSince, this.closedAt, this.version, this.createdAt, this.updatedAt, this.revertsTicketId, this.revertedByTicketId);
	}

	private Ticket moved(TicketStatus next, int revision) {
		requireCanBecome(next);
		return new Ticket(this.id, this.tenantId, this.type, next, this.approvalPath, this.authorUserId,
				this.actingFacultyId, this.description, revision, this.lecture, this.expiresAt, this.conflicted,
				this.conflictedSince, this.closedAt, this.version, this.createdAt, this.updatedAt, this.revertsTicketId, this.revertedByTicketId);
	}

	private Ticket closed(TicketStatus next, Instant at) {
		requireCanBecome(next);
		return new Ticket(this.id, this.tenantId, this.type, next, this.approvalPath, this.authorUserId,
				this.actingFacultyId, this.description, this.revisionNo, this.lecture, this.expiresAt, this.conflicted,
				this.conflictedSince, at, this.version, this.createdAt, this.updatedAt, this.revertsTicketId, this.revertedByTicketId);
	}

	private void requireCanBecome(TicketStatus next) {
		if (!this.status.canBecome(next)) {
			throw new IllegalStateException("A ticket that is " + this.status + " cannot become " + next);
		}
	}

}
