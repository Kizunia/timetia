package com.kibootia.backend.rescheduling.domain;

import java.util.Set;

/**
 * Where a ticket is in its life (ticket lifecycle). {@code Open} and {@code Approved} do not exist: with self-approval a
 * ticket goes from draft to applied in one step, so nobody could ever see them.
 *
 * <p>Applied is the end of a ticket's own life: undoing it is a new ticket, and this one becomes reverted. Withdrawn,
 * expired and reverted are final. Rejected is not: the author edits and resubmits it ({@code REJECTED → DRAFT}), or escalates
 * it to the HOD ({@code REJECTED → IN_REVIEW}).
 */
public enum TicketStatus {

	/** Being filled in; not seen by others. */
	DRAFT,

	/** Sent for review and waiting for the people asked. */
	IN_REVIEW,

	/** Written to the change overlay. */
	APPLIED,

	/** A reviewer rejected it, with a reason. */
	REJECTED,

	/** The author took it back before it was applied. */
	WITHDRAWN,

	/** The lecture started before it was applied. */
	EXPIRED,

	/** An applied change was undone by another ticket. */
	REVERTED;

	/** The states a ticket in this state can move to. */
	public Set<TicketStatus> next() {
		return switch (this) {
			case DRAFT -> Set.of(APPLIED, IN_REVIEW, WITHDRAWN, EXPIRED);
			case IN_REVIEW -> Set.of(APPLIED, REJECTED, WITHDRAWN, EXPIRED);
			case REJECTED -> Set.of(DRAFT, IN_REVIEW);
			case APPLIED -> Set.of(REVERTED);
			case WITHDRAWN, EXPIRED, REVERTED -> Set.of();
		};
	}

	public boolean canBecome(TicketStatus other) {
		return next().contains(other);
	}

	/** Whether the ticket's own life is over and nothing can happen to it any more. */
	public boolean isFinal() {
		return next().isEmpty();
	}

	/** Whether the ticket has not yet been applied and can still lapse when the lecture starts. */
	public boolean isOpen() {
		return this == DRAFT || this == IN_REVIEW;
	}

}
