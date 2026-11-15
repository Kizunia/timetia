package com.kibootia.backend.rescheduling.application;

import java.time.Instant;
import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

/**
 * One person asked to review a ticket, and their answer (data model {@code TicketReview}).
 *
 * @param revisionNo the revision of the ticket this review belongs to; only the current revision's reviews count
 * @param reason why it was rejected; required for a rejection, and optional otherwise
 * @param decidedAt when they answered; null while pending
 * @param escalation whether the author asked them after a rejection, to settle it
 * @param escalatedFromReviewId the rejection an escalation answers
 */
public record TicketReview(UUID id, UUID ticketId, int revisionNo, UUID reviewerUserId, ReviewerCapacity capacity,
		ReviewStatus status, String reason, Instant decidedAt, boolean escalation, UUID escalatedFromReviewId) {

}
