package com.kibootia.backend.reference.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/**
 * A department, the top of the campus structure and the {@code DEPARTMENT} scope a role can be assigned in. Persistence
 * only: it never leaves this package (ADR 0004). The key, tenant, version, audit columns and soft delete come from the
 * platform base type; the unique key on (tenant, code, active marker) is in the migration, so a code names at most one
 * live department within a tenant (integration contract).
 */
@Entity
@Table(name = "department")
class DepartmentEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	protected DepartmentEntity() {
	}

	DepartmentEntity(String code, String name) {
		this.code = code;
		this.name = name;
	}

	String getCode() {
		return this.code;
	}

	String getName() {
		return this.name;
	}

	void rename(String name) {
		this.name = name;
	}

}
