package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.timetable.application.VersionStatus;

/**
 * The table row of a timetable version. Persistence only (ADR 0004). The generated {@code published_from} column and the
 * unique key on it are in the migration; they keep two published versions of a division from taking effect on one day.
 */
@Entity
@Table(name = "timetable_version")
class TimetableVersionEntity extends BaseEntity {

	@Column(name = "division_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID divisionId;

	@Column(name = "term_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID termId;

	@Column(name = "effective_from", nullable = false, updatable = false)
	private LocalDate effectiveFrom;

	@Enumerated(EnumType.STRING)
	@Column(name = "status", nullable = false, length = 12)
	private VersionStatus status;

	protected TimetableVersionEntity() {
	}

	TimetableVersionEntity(UUID divisionId, UUID termId, LocalDate effectiveFrom) {
		this.divisionId = divisionId;
		this.termId = termId;
		this.effectiveFrom = effectiveFrom;
		this.status = VersionStatus.DRAFT;
	}

	UUID getDivisionId() {
		return this.divisionId;
	}

	UUID getTermId() {
		return this.termId;
	}

	LocalDate getEffectiveFrom() {
		return this.effectiveFrom;
	}

	VersionStatus getStatus() {
		return this.status;
	}

	void publish() {
		this.status = VersionStatus.PUBLISHED;
	}

}
