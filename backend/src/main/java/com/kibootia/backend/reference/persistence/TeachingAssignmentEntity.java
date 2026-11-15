package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.academic.SessionType;
import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/**
 * The table row of a teaching assignment. Persistence only (ADR 0004). The generated {@code batch_scope} and
 * {@code active_marker} columns and the batch rule live in the migration, so they are not mapped here.
 */
@Entity
@Table(name = "teaching_assignment")
class TeachingAssignmentEntity extends SoftDeletableEntity {

	@Column(name = "division_id", nullable = false, columnDefinition = "binary(16)")
	private UUID divisionId;

	@Column(name = "batch_id", columnDefinition = "binary(16)")
	private UUID batchId;

	@Column(name = "subject_id", nullable = false, columnDefinition = "binary(16)")
	private UUID subjectId;

	@Column(name = "faculty_id", nullable = false, columnDefinition = "binary(16)")
	private UUID facultyId;

	@Enumerated(EnumType.STRING)
	@Column(name = "session_type", nullable = false, length = 20)
	private SessionType sessionType;

	protected TeachingAssignmentEntity() {
	}

	TeachingAssignmentEntity(UUID divisionId, UUID batchId, UUID subjectId, UUID facultyId, SessionType sessionType) {
		this.divisionId = divisionId;
		this.batchId = batchId;
		this.subjectId = subjectId;
		this.facultyId = facultyId;
		this.sessionType = sessionType;
	}

	UUID getDivisionId() {
		return this.divisionId;
	}

	UUID getBatchId() {
		return this.batchId;
	}

	UUID getSubjectId() {
		return this.subjectId;
	}

	UUID getFacultyId() {
		return this.facultyId;
	}

	SessionType getSessionType() {
		return this.sessionType;
	}

}
