package com.kibootia.backend.rescheduling.web;

import java.time.Instant;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * One person asked to review a ticket, and their answer. Only the reviews of the ticket's current revision count towards
 * whether it is approved; the earlier revisions' are the history. {@code escalation} marks the HOD the author asked after a
 * rejection.
 */
public record ReviewResponse(
		@Schema(requiredMode = REQUIRED) UUID reviewerUserId,
		@Schema(nullable = true, description = "The reviewer's name; absent when the account is gone.") String reviewerName,
		@Schema(requiredMode = REQUIRED) ReviewerCapacity capacity,
		@Schema(requiredMode = REQUIRED) ReviewStatus status,
		@Schema(nullable = true) String reason,
		@Schema(nullable = true) Instant decidedAt,
		@Schema(requiredMode = REQUIRED) int revisionNo,
		@Schema(requiredMode = REQUIRED) boolean escalation) {

}
