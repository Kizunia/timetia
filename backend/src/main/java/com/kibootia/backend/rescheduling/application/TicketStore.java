package com.kibootia.backend.rescheduling.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.kibootia.backend.rescheduling.domain.CheckTrigger;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.timetable.LectureConflict;
import com.kibootia.backend.timetable.LectureSlot;

/**
 * Where tickets, their changes and the conflict checks made on them are kept. The use cases depend on this port, never on
 * JPA (ADR 0003). Reads and writes act on the current tenant's rows.
 */
public interface TicketStore {

	/** Stores a new draft ticket with its change. */
	Ticket insert(NewTicket ticket);

	Optional<Ticket> find(UUID id);

	/** The changes a ticket makes, in order. */
	List<TicketChange> changesOf(UUID ticketId);

	/** The changes of these tickets, by ticket, so a page of tickets costs one query and not one each. */
	Map<UUID, List<TicketChange>> changesOf(Collection<UUID> ticketIds);

	/**
	 * Saves the state of a ticket if it is still the version that was read; otherwise fails with an optimistic locking
	 * failure, so two people acting on one ticket cannot both succeed.
	 */
	Ticket update(Ticket ticket);

	/** Replaces the changes of a ticket that is not yet applied, for an edit; there are as many as before. */
	void replaceChanges(UUID ticketId, List<NewTicket.Change> changes);

	/** Records where the lecture was before this change was applied. */
	void recordBefore(UUID changeId, LectureSlot before, UUID beforeFacultyId);

	/** Records the lecture an applied extra-lecture ticket made. */
	void recordCreated(UUID changeId, UUID occurrenceId);

	Page<Ticket> search(TicketQuery query, Pageable pageable);

	/** Records that the ticket's conflicts were checked, and what was found; an empty list means it was clear. */
	void recordCheck(UUID ticketId, CheckTrigger trigger, List<LectureConflict> conflicts);

	/** What the latest check of the ticket found; empty when it found nothing or there was none. */
	List<LectureConflict> latestCheck(UUID ticketId);

	// --- reviews ----------------------------------------------------------------------------------------------------

	/** Every review of the ticket, of every revision, oldest revision first. */
	List<TicketReview> reviewsOf(UUID ticketId);

	/** The people asked to review the ticket in this revision. */
	Set<UUID> reviewerUserIds(UUID ticketId, int revisionNo);

	/** Asks these people to review the ticket in this revision. They start pending. */
	List<TicketReview> insertReviews(UUID ticketId, int revisionNo, List<NewReview> reviews);

	/** Records a reviewer's answer. */
	TicketReview decide(UUID reviewId, ReviewStatus status, String reason, Instant at);

	/** Makes every review of the revision pending again, because the plan they answered has changed. */
	void resetDecisions(UUID ticketId, int revisionNo);

	/**
	 * Makes the people asked in this revision exactly these: those already asked keep their answers, others are dropped,
	 * and new ones start pending.
	 */
	void syncReviewers(UUID ticketId, int revisionNo, List<NewReview> reviewers);

	// --- finding tickets --------------------------------------------------------------------------------------------

	/**
	 * The tickets under review that could be affected by a change to these lectures, or by something moving on or off these
	 * dates: those about the same lecture, and those that want a slot on one of the dates.
	 */
	List<Ticket> inReviewAffectedBy(Set<UUID> lectureIds, Set<LocalDate> dates);

	/** Whether a revert of this ticket has been raised and is not over: a draft, or under review. */
	boolean hasOpenRevert(UUID ticketId);

	/** Tickets not yet applied whose lecture has started by this instant, oldest first, at most {@code limit}. */
	List<Ticket> openExpiredBy(Instant instant, int limit);

}
