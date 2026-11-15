package com.kibootia.backend.reference.persistence;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/** The table row of a faculty member. Persistence only (ADR 0004). */
@Entity
@Table(name = "faculty")
class FacultyEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	@Column(name = "department_id", nullable = false, columnDefinition = "binary(16)")
	private UUID departmentId;

	@Column(name = "user_id", columnDefinition = "binary(16)")
	private UUID userId;

	protected FacultyEntity() {
	}

	FacultyEntity(String code, String name, UUID departmentId, UUID userId) {
		this.code = code;
		this.name = name;
		this.departmentId = departmentId;
		this.userId = userId;
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

	UUID getUserId() {
		return this.userId;
	}

	void rename(String name) {
		this.name = name;
	}

}
