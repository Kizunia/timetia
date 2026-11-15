package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/**
 * A batch (a lab group, such as B1), which belongs to a division and is the {@code BATCH} scope a BR is assigned in.
 * Persistence only (ADR 0004). The unique key on (tenant, code, active marker) is in the migration; the division it
 * belongs to is kept by id.
 */
@Entity
@Table(name = "batch")
class BatchEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "division_id", nullable = false, columnDefinition = "binary(16)")
	private UUID divisionId;

	protected BatchEntity() {
	}

	BatchEntity(String code, String name, UUID divisionId) {
		this.code = code;
		this.name = name;
		this.divisionId = divisionId;
	}

	String getCode() {
		return this.code;
	}

	String getName() {
		return this.name;
	}

	UUID getDivisionId() {
		return this.divisionId;
	}

	void rename(String name) {
		this.name = name;
	}

}
