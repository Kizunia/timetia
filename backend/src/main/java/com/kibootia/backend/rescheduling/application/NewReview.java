package com.kibootia.backend.rescheduling.application;

import java.util.UUID;

import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

/** A person to ask to review a ticket. It starts pending. */
public record NewReview(UUID reviewerUserId, ReviewerCapacity capacity, boolean escalation, UUID escalatedFromReviewId) {

	public static NewReview of(UUID reviewerUserId, ReviewerCapacity capacity) {
		return new NewReview(reviewerUserId, capacity, false, null);
	}

}
