package com.kibootia.backend.rescheduling.application;

import java.util.List;

import com.kibootia.backend.timetable.LectureConflict;

/**
 * A ticket with the changes it makes, what the latest check found, and who was asked to review it. A list leaves
 * {@code conflicts} and {@code reviews} empty.
 *
 * @param changes the changes the ticket makes, in order: one, or two for an exchange
 * @param reviews every review of the ticket, of every revision, oldest first; the earlier revisions are history
 */
public record TicketDetail(Ticket ticket, List<TicketChange> changes, List<LectureConflict> conflicts,
		List<TicketReview> reviews) {

	/** The first change: the only one of most tickets, and for an exchange the one about the author's own lecture. */
	public TicketChange change() {
		return this.changes.get(0);
	}

}
