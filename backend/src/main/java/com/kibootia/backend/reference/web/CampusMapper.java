package com.kibootia.backend.reference.web;

import com.kibootia.backend.reference.application.Batch;
import com.kibootia.backend.reference.application.Department;
import com.kibootia.backend.reference.application.Division;

/** Converts campus application models into API responses by hand (ADR 0005). */
final class CampusMapper {

	private CampusMapper() {
	}

	static DepartmentResponse toResponse(Department department) {
		return new DepartmentResponse(department.id(), department.code(), department.name(), department.version(),
				department.createdAt(), department.updatedAt());
	}

	static DivisionResponse toResponse(Division division) {
		return new DivisionResponse(division.id(), division.code(), division.name(), division.departmentId(),
				division.version(), division.createdAt(), division.updatedAt());
	}

	static BatchResponse toResponse(Batch batch) {
		return new BatchResponse(batch.id(), batch.code(), batch.name(), batch.divisionId(), batch.version(),
				batch.createdAt(), batch.updatedAt());
	}

}
