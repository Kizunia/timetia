package com.kibootia.backend.issues.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.BaseEntity;

/** A ticket linked to an issue as its answer. Persistence only (ADR 0004). */
@Entity
@Table(name = "issue_ticket_link")
class IssueLinkEntity extends BaseEntity {

	@Column(name = "issue_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID issueId;

	@Column(name = "ticket_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ticketId;

	@Column(name = "linked_by_user_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID linkedByUserId;

	protected IssueLinkEntity() {
	}

	IssueLinkEntity(UUID issueId, UUID ticketId, UUID linkedByUserId) {
		this.issueId = issueId;
		this.ticketId = ticketId;
		this.linkedByUserId = linkedByUserId;
	}

	UUID getIssueId() {
		return this.issueId;
	}

	UUID getTicketId() {
		return this.ticketId;
	}

}
