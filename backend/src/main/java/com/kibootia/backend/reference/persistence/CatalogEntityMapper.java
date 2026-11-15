package com.kibootia.backend.reference.persistence;

import com.kibootia.backend.reference.application.AcademicTerm;
import com.kibootia.backend.reference.application.Faculty;
import com.kibootia.backend.reference.application.Room;
import com.kibootia.backend.reference.application.Subject;

/** Converts between the catalog persistence entities and the application models by hand (ADR 0005). */
final class CatalogEntityMapper {

	private CatalogEntityMapper() {
	}

	static Subject toModel(SubjectEntity entity) {
		return new Subject(entity.getId(), entity.getCode(), entity.getName(), entity.getVersion(), entity.getCreatedAt(),
				entity.getUpdatedAt());
	}

	static Room toModel(RoomEntity entity) {
		return new Room(entity.getId(), entity.getCode(), entity.getName(), entity.getRoomType(), entity.getCapacity(),
				entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static Faculty toModel(FacultyEntity entity) {
		return new Faculty(entity.getId(), entity.getCode(), entity.getName(), entity.getDepartmentId(),
				entity.getUserId(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

	static AcademicTerm toModel(AcademicTermEntity entity) {
		return new AcademicTerm(entity.getId(), entity.getCode(), entity.getName(), entity.getStartDate(),
				entity.getEndDate(), entity.getVersion(), entity.getCreatedAt(), entity.getUpdatedAt());
	}

}
