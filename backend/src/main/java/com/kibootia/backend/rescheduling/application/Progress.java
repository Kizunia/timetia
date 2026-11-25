package com.kibootia.backend.rescheduling.application;

/**
 * What came of an action that can end in the ticket being applied: sending it for review, answering a review, taking a
 * rejection to the HOD, or applying it. The action that was asked for has been done in every case but one, and
 * {@code detail} is the ticket as it is now.
 *
 * <p>A refusal that has to be recorded is returned, not thrown, so that what was found is committed with the rest: the
 * conflicts that blocked the change, or the expiry of a ticket whose lecture started.
 */
public record Progress(Outcome outcome, TicketDetail detail) {

	public enum Outcome {

		/** Done, and the ticket is as the detail says (still open, rejected, or withdrawn). */
		DONE,

		/** The ticket was applied. */
		APPLIED,

		/** The ticket could not be applied because something is in the way; it is flagged and unchanged otherwise. */
		BLOCKED,

		/** The lecture started before the ticket was applied, so the ticket expired and the action was not done. */
		EXPIRED

	}

}
