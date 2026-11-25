package com.kibootia.backend.rescheduling;

import java.util.Set;
import java.util.UUID;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.events.DomainEvent;
import com.kibootia.backend.platform.events.EventContext;

/**
 * Something happened to a ticket that people should be told about (rules: notification triggers). Part of the module's public
 * contract: the notifications module reacts to it, through a durable listener, and decides who is told what. The event says
 * who is on the ticket and where the lecture is, never who should be told, so the rules about audiences stay in one place.
 *
 * <p>Expiry sends no event: an expired ticket tells nobody. For {@link Kind#REVERTED} the ticket is the one that was undone,
 * so the people told are those who were told of the original change.
 *
 * @param actorUserId who did it
 * @param authorUserId who wrote the ticket
 * @param reviewerUserIds the people asked to review it
 * @param facultyUserIds the faculty it involves: who teaches the lecture, the other teacher of an exchange, a substitute
 * @param batchIds every batch the lecture is for; the students of those batches are affected
 */
public record TicketChanged(UUID ticketId, Kind kind, UUID actorUserId, UUID authorUserId, Set<UUID> reviewerUserIds,
		Set<UUID> facultyUserIds, UUID departmentId, UUID divisionId, Set<UUID> batchIds, SessionType sessionType,
		EventContext context) implements DomainEvent {

	/** What happened. */
	public enum Kind {

		/** Sent for review: the reviewers are asked. */
		REVIEW_REQUESTED,

		/** A reviewer approved it: the author is told. */
		REVIEW_APPROVED,

		/** A reviewer rejected it: the author is told. */
		REVIEW_REJECTED,

		/** It was applied: everyone affected is told. */
		APPLIED,

		/** Something is in the way of a ticket under review, or of applying it. */
		CONFLICTED,

		/** The plan was edited and the approvals were cleared: the reviewers are asked again. */
		EDITED,

		/** The author withdrew it from review. */
		WITHDRAWN,

		/** It was undone by an applied revert: everyone who was told of the change is told. */
		REVERTED

	}

}
