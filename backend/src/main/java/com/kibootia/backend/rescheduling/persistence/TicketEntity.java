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

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.rescheduling.application.NewTicket;
import com.kibootia.backend.rescheduling.application.Ticket;
import com.kibootia.backend.rescheduling.domain.ApprovalPath;
import com.kibootia.backend.rescheduling.domain.TicketStatus;
import com.kibootia.backend.rescheduling.domain.TicketType;

/**
 * The table row of a ticket. Persistence only (ADR 0004). What identifies the ticket and the lecture it concerns never
 * changes; the state, the description, the expiry and the conflict flag do, and {@link #update} is the one place that
 * writes them.
 */
@Entity
@Table(name = "timetable_change_ticket")
class TicketEntity extends BaseEntity {

	@Enumerated(EnumType.STRING)
	@Column(name = "ticket_type", nullable = false, updatable = false, length = 20)
	private TicketType type;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 12)
	private TicketStatus status;

	@Enumerated(EnumType.STRING)
	@Column(name = "approval_path", length = 10)
	private ApprovalPath approvalPath;

	@Column(name = "author_user_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID authorUserId;

	@Column(name = "acting_faculty_id", updatable = false, columnDefinition = "binary(16)")
	private UUID actingFacultyId;

	@Column(name = "description", length = 1000)
	private String description;

	// A SMALLINT column held in an int, so Hibernate's schema validation must be told the column's type.
	@JdbcTypeCode(SqlTypes.SMALLINT)
	@Column(name = "revision_no", nullable = false)
	private int revisionNo;

	@Column(name = "assignment_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID assignmentId;

	@Column(name = "department_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID departmentId;

	@Column(name = "division_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID divisionId;

	@Column(name = "batch_id", updatable = false, columnDefinition = "binary(16)")
	private UUID batchId;

	@Enumerated(EnumType.STRING)
	@Column(name = "session_type", nullable = false, updatable = false, length = 10)
	private SessionType sessionType;

	@Column(name = "faculty_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID facultyId;

	@Column(name = "expires_at", nullable = false)
	private Instant expiresAt;

	@Column(name = "is_conflicted", nullable = false)
	private boolean conflicted;

	@Column(name = "conflicted_since")
	private Instant conflictedSince;

	@Column(name = "closed_at")
	private Instant closedAt;

	protected TicketEntity() {
	}

	TicketEntity(NewTicket ticket) {
		this.type = ticket.type();
		this.status = TicketStatus.DRAFT;
		this.authorUserId = ticket.authorUserId();
		this.actingFacultyId = ticket.actingFacultyId();
		this.description = ticket.description();
		this.revisionNo = 1;
		this.assignmentId = ticket.lecture().assignmentId();
		this.departmentId = ticket.lecture().departmentId();
		this.divisionId = ticket.lecture().divisionId();
		this.batchId = ticket.lecture().batchId();
		this.sessionType = ticket.lecture().sessionType();
		this.facultyId = ticket.lecture().facultyId();
		this.expiresAt = ticket.expiresAt();
	}

	/** Writes what can change from the ticket the use case built. */
	void update(Ticket ticket) {
		this.status = ticket.status();
		this.approvalPath = ticket.approvalPath();
		this.description = ticket.description();
		this.revisionNo = ticket.revisionNo();
		this.expiresAt = ticket.expiresAt();
		this.conflicted = ticket.conflicted();
		this.conflictedSince = ticket.conflictedSince();
		this.closedAt = ticket.closedAt();
	}

	TicketType getType() {
		return this.type;
	}

	TicketStatus getStatus() {
		return this.status;
	}

	ApprovalPath getApprovalPath() {
		return this.approvalPath;
	}

	UUID getAuthorUserId() {
		return this.authorUserId;
	}

	UUID getActingFacultyId() {
		return this.actingFacultyId;
	}

	String getDescription() {
		return this.description;
	}

	int getRevisionNo() {
		return this.revisionNo;
	}

	UUID getAssignmentId() {
		return this.assignmentId;
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

	SessionType getSessionType() {
		return this.sessionType;
	}

	UUID getFacultyId() {
		return this.facultyId;
	}

	Instant getExpiresAt() {
		return this.expiresAt;
	}

	boolean isConflicted() {
		return this.conflicted;
	}

	Instant getConflictedSince() {
		return this.conflictedSince;
	}

	Instant getClosedAt() {
		return this.closedAt;
	}

}
