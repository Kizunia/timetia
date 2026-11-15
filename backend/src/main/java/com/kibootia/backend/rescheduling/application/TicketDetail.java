package com.kibootia.backend.rescheduling.application;

import java.util.List;

import com.kibootia.backend.timetable.LectureConflict;

/**
 * A ticket with the change it makes, what the latest check found, and who was asked to review it. A list leaves
 * {@code conflicts} and {@code reviews} empty.
 *
 * @param reviews every review of the ticket, of every revision, oldest first; the earlier revisions are history
 */
public record TicketDetail(Ticket ticket, TicketChange change, List<LectureConflict> conflicts,
		List<TicketReview> reviews) {

}
