package com.kibootia.backend.reference.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/** The table row of a subject. Persistence only (ADR 0004). */
@Entity
@Table(name = "subject")
class SubjectEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 150)
	private String name;

	protected SubjectEntity() {
	}

	SubjectEntity(String code, String name) {
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
