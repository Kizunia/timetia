package com.kibootia.backend.timetable.persistence;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.kibootia.backend.platform.persistence.BaseEntity;
import com.kibootia.backend.timetable.domain.ClaimKey;
import com.kibootia.backend.timetable.domain.ClaimKind;

/**
 * One thing a lecture holds for one hour. Persistence only (ADR 0004). The unique key on (tenant, kind, resource, date,
 * hour) is in the migration: it is what keeps a teacher, a room or students from being booked twice.
 */
@Entity
@Table(name = "occurrence_claim")
class OccurrenceClaimEntity extends BaseEntity {

	@Column(name = "occurrence_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID occurrenceId;

	@Enumerated(EnumType.STRING)
	@Column(name = "claim_kind", nullable = false, updatable = false, length = 16)
	private ClaimKind claimKind;

	@Column(name = "resource_id", nullable = false, updatable = false, columnDefinition = "binary(16)")
	private UUID resourceId;

	@Column(name = "claim_date", nullable = false, updatable = false)
	private LocalDate claimDate;

	// A TINYINT column (8 to 19) held in an int, so Hibernate's schema validation must be told the column's type.
	@JdbcTypeCode(SqlTypes.TINYINT)
	@Column(name = "cell_hour", nullable = false, updatable = false)
	private int cellHour;

	protected OccurrenceClaimEntity() {
	}

	OccurrenceClaimEntity(UUID occurrenceId, ClaimKey key) {
		this.occurrenceId = occurrenceId;
		this.claimKind = key.kind();
		this.resourceId = key.resourceId();
		this.claimDate = key.date();
		this.cellHour = key.hour();
	}

	UUID getOccurrenceId() {
		return this.occurrenceId;
	}

	ClaimKey key() {
		return new ClaimKey(this.claimKind, this.resourceId, this.claimDate, this.cellHour);
	}

}
