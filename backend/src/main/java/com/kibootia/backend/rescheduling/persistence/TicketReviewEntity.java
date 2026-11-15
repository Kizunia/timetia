package com.kibootia.backend.rescheduling.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.rescheduling.application.NewReview;
import com.kibootia.backend.rescheduling.domain.ReviewStatus;
import com.kibootia.backend.rescheduling.domain.ReviewerCapacity;

/**
 * The table row of one person asked to review a ticket, and their answer. Persistence only (ADR 0004). The unique key on
 * (ticket, revision, person, capacity) is in the migration.
 */
@Entity
@Table(name = "ticket_review")
class TicketReviewEntity extends BaseEntity {

	@Column(name = "ticket_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ticketId;

	// A SMALLINT column held in an int, so Hibernate's schema validation must be told the column's type.
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "ticket_revision_no", nullable = false, updatable = false)
	private int ticketRevisionNo;

	@Column(name = "reviewer_user_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID reviewerUserId;

	@Enumerated(EnumType.STRING)
	@Column(name = "reviewer_capacity", nullable = false, updatable = false, length = 20)
	private ReviewerCapacity capacity;

	@Enumerated(EnumType.STRING)
	@Column(name = "review_status", nullable = false, length = 12)
	private ReviewStatus status;

	@Column(name = "decision_reason", length = 500)
	private String reason;

	@Column(name = "decided_at")
	private Instant decidedAt;

	@Column(name = "is_escalation", nullable = false, updatable = false)
	private boolean escalation;

	@Column(name = "escalated_from_review_id", updatable = false, columnDefinition = "binary(16)")
	private UUID escalatedFromReviewId;

	protected TicketReviewEntity() {
	}

	TicketReviewEntity(UUID ticketId, int revisionNo, NewReview review) {
		this.ticketId = ticketId;
		this.ticketRevisionNo = revisionNo;
		this.reviewerUserId = review.reviewerUserId();
		this.capacity = review.capacity();
		this.status = ReviewStatus.PENDING;
		this.escalation = review.escalation();
		this.escalatedFromReviewId = review.escalatedFromReviewId();
	}

	void decide(ReviewStatus answer, String why, Instant at) {
		this.status = answer;
		this.reason = why;
		this.decidedAt = at;
	}

	/** Back to waiting for an answer, because what was answered has changed. */
	void reset() {
		this.status = ReviewStatus.PENDING;
		this.reason = null;
		this.decidedAt = null;
	}

	UUID getTicketId() {
		return this.ticketId;
	}

	int getTicketRevisionNo() {
		return this.ticketRevisionNo;
	}

	UUID getReviewerUserId() {
		return this.reviewerUserId;
	}

	ReviewerCapacity getCapacity() {
		return this.capacity;
	}

	ReviewStatus getStatus() {
		return this.status;
	}

	String getReason() {
		return this.reason;
	}

	Instant getDecidedAt() {
		return this.decidedAt;
	}

	boolean isEscalation() {
		return this.escalation;
	}

	UUID getEscalatedFromReviewId() {
		return this.escalatedFromReviewId;
	}

}
