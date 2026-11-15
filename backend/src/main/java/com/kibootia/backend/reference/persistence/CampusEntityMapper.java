package com.kibootia.backend.reference.persistence;

import com.kibootia.backend.reference.application.Batch;
import com.kibootia.backend.reference.application.Department;
import com.kibootia.backend.reference.application.Division;

/** Converts between the campus persistence entities and the application models by hand (ADR 0005). */
final class CampusEntityMapper {

	private CampusEntityMapper() {
	}

	static Department toModel(DepartmentEntity entity) {
		return new Department(entity.getId(), entity.getCode(), entity.getName(), entity.getVersion(),
				entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static Division toModel(DivisionEntity entity) {
		return new Division(entity.getId(), entity.getCode(), entity.getName(), entity.getDepartmentId(),
				entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static Batch toModel(BatchEntity entity) {
		return new Batch(entity.getId(), entity.getCode(), entity.getName(), entity.getDivisionId(), entity.getVersion(),
				entity.getCreatedAt(), entity.getUpdatedAt());
	}

}
