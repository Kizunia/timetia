package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/**
 * A division (a class, such as SY-CSSE-B), which belongs to a department and is the {@code DIVISION} scope a CR is
 * assigned in. Persistence only (ADR 0004). The unique key on (tenant, code, active marker) is in the migration; the
 * department it belongs to is kept by id, resolved through the department table when needed.
 */
@Entity
@Table(name = "division")
class DivisionEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 40)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "department_id", nullable = false, columnDefinition = "binary(16)")
	private UUID departmentId;

	protected DivisionEntity() {
	}

	DivisionEntity(String code, String name, UUID departmentId) {
		this.code = code;
		this.name = name;
		this.departmentId = departmentId;
	}

	String getCode() {
		return this.code;
	}

	String getName() {
		return this.name;
	}

	UUID getDepartmentId() {
		return this.departmentId;
	}

	void rename(String name) {
		this.name = name;
	}

}
