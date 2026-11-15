package com.kibootia.backend.rescheduling.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.timetable.ConflictType;
import com.kibootia.backend.timetable.LectureConflict;

/** One thing a conflict check found in the way. Persistence only (ADR 0004). */
@Entity
@Table(name = "conflict_finding")
class ConflictFindingEntity extends BaseEntity {

	@Column(name = "run_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID runId;

	@Column(name = "ticket_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID ticketId;

	@Enumerated(EnumType.STRING)
	@Column(name = "conflict_type", nullable = false, updatable = false, length = 20)
	private ConflictType type;

	@Column(name = "message", nullable = false, updatable = false, length = 500)
	private String message;

	@Column(name = "occurrence_id", updatable = false, columnDefinition = "binary(16)")
	private UUID occurrenceId;

	@Column(name = "conflicting_occurrence_id", updatable = false, columnDefinition = "binary(16)")
	private UUID conflictingOccurrenceId;

	@Column(name = "conflicting_ticket_id", updatable = false, columnDefinition = "binary(16)")
	private UUID conflictingTicketId;

	protected ConflictFindingEntity() {
	}

	ConflictFindingEntity(UUID runId, UUID ticketId, LectureConflict conflict) {
		this.runId = runId;
		this.ticketId = ticketId;
		this.type = conflict.type();
		// A message is a sentence for a person; it is cut rather than refused if it is ever longer than the column.
		this.message = (conflict.message().length() > 500) ? conflict.message().substring(0, 500) : conflict.message();
		this.occurrenceId = conflict.occurrenceId();
		this.conflictingOccurrenceId = conflict.conflictingOccurrenceId();
		this.conflictingTicketId = conflict.conflictingTicketId();
	}

	LectureConflict toConflict() {
		return new LectureConflict(this.type, this.occurrenceId, this.message, this.conflictingOccurrenceId,
				this.conflictingTicketId);
	}

}
