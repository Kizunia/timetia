package com.kibootia.backend.reference.web;

import com.kibootia.backend.reference.application.AcademicTerm;
import com.kibootia.backend.reference.application.Faculty;
import com.kibootia.backend.reference.application.Room;
import com.kibootia.backend.reference.application.Subject;

/** Converts catalog application models into API responses by hand (ADR 0005). */
final class CatalogMapper {

	private CatalogMapper() {
	}

	static SubjectResponse toResponse(Subject subject) {
		return new SubjectResponse(subject.id(), subject.code(), subject.name(), subject.version(), subject.createdAt(),
				subject.updatedAt());
	}

	static RoomResponse toResponse(Room room) {
		return new RoomResponse(room.id(), room.code(), room.name(), room.roomType(), room.capacity(), room.version(),
				room.createdAt(), room.updatedAt());
	}

	static FacultyResponse toResponse(Faculty faculty) {
		return new FacultyResponse(faculty.id(), faculty.code(), faculty.name(), faculty.departmentId(), faculty.userId(),
				faculty.version(), faculty.createdAt(), faculty.updatedAt());
	}

	static TermResponse toResponse(AcademicTerm term) {
		return new TermResponse(term.id(), term.code(), term.name(), term.startDate(), term.endDate(), term.version(),
				term.createdAt(), term.updatedAt());
	}

}
