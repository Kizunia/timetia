package com.kibootia.backend.issues.persistence;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.issues.application.Issue;
import com.kibootia.backend.issues.application.NewIssue;
import com.kibootia.backend.issues.domain.IssueStatus;
import com.kibootia.backend.platform.persistence.BaseEntity;

/** The table row of an issue. Persistence only (ADR 0004). Where it was opened and by whom never change; its state does. */
@Entity
@Table(name = "issue")
class IssueEntity extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "session_type", nullable = false, updatable = false, length = 10)
	private SessionType sessionType;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 8)
	private IssueStatus status;

	@Column(name = "department_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID departmentId;

	@Column(name = "division_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID divisionId;

	@Column(name = "batch_id", updatable = false, columnDefinition = "binary(16)")
	private UUID batchId;

	@Column(name = "title", nullable = false, updatable = false, length = 200)
	private String title;

	@Column(name = "description", updatable = false, length = 2000)
	private String description;

	@Column(name = "opener_user_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID openerUserId;

	@Column(name = "closed_at")
	private Instant closedAt;

	@Column(name = "closed_by_user_id", columnDefinition = "binary(16)")
	private UUID closedByUserId;

	protected IssueEntity() {
	}

	IssueEntity(NewIssue issue) {
		this.sessionType = issue.sessionType();
		this.status = IssueStatus.OPEN;
		this.departmentId = issue.departmentId();
		this.divisionId = issue.divisionId();
		this.batchId = issue.batchId();
		this.title = issue.title();
		this.description = issue.description();
		this.openerUserId = issue.openerUserId();
	}

	/** Writes what can change from the issue the use case built. */
	void update(Issue issue) {
		this.status = issue.status();
		this.closedAt = issue.closedAt();
		this.closedByUserId = issue.closedByUserId();
	}

	SessionType getSessionType() {
		return this.sessionType;
	}

	IssueStatus getStatus() {
		return this.status;
	}

	UUID getDepartmentId() {
		return this.departmentId;
	}

	UUID getDivisionId() {
		return this.divisionId;
	}

	UUID getBatchId() {
		return this.batchId;
	}

	String getTitle() {
		return this.title;
	}

	String getDescription() {
		return this.description;
	}

	UUID getOpenerUserId() {
		return this.openerUserId;
	}

	Instant getClosedAt() {
		return this.closedAt;
	}

	UUID getClosedByUserId() {
		return this.closedByUserId;
	}

}
