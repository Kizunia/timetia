package com.kibootia.backend.reference.persistence;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.kibootia.backend.platform.persistence.SoftDeletableEntity;

/** The table row of an academic term. Persistence only (ADR 0004). */
@Entity
@Table(name = "academic_term")
class AcademicTermEntity extends SoftDeletableEntity {

	@Column(name = "code", nullable = false, length = 30)
	private String code;

	@Column(name = "name", nullable = false, length = 120)
	private String name;

	@Column(name = "start_date", nullable = false)
	private LocalDate startDate;

	@Column(name = "end_date", nullable = false)
	private LocalDate endDate;

	protected AcademicTermEntity() {
	}

	AcademicTermEntity(String code, String name, LocalDate startDate, LocalDate endDate) {
		this.code = code;
		this.name = name;
		this.startDate = startDate;
		this.endDate = endDate;
	}

	String getCode() {
		return this.code;
	}

	String getName() {
		return this.name;
	}

	LocalDate getStartDate() {
		return this.startDate;
	}

	LocalDate getEndDate() {
		return this.endDate;
	}

	void rename(String name) {
		this.name = name;
	}

}
