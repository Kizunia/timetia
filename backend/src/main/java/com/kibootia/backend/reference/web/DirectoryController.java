package com.kibootia.backend.reference.web;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.reference.application.DirectoryService;
import com.kibootia.backend.reference.application.DirectoryService.FacultyRef;
import com.kibootia.backend.reference.application.DirectoryService.MyCampus;
import com.kibootia.backend.reference.web.DirectoryResponses.DirectoryBatchResponse;
import com.kibootia.backend.reference.web.DirectoryResponses.DirectoryDepartmentResponse;
import com.kibootia.backend.reference.web.DirectoryResponses.DirectoryDivisionResponse;
import com.kibootia.backend.reference.web.DirectoryResponses.DirectoryFacultyResponse;
import com.kibootia.backend.reference.web.DirectoryResponses.DirectoryRoomResponse;
import com.kibootia.backend.reference.web.DirectoryResponses.MyAssignmentResponse;
import com.kibootia.backend.reference.web.DirectoryResponses.MyCampusResponse;

/**
 * The campus names any verified person may read, to show a name for an id and to fill pickers. Every list is unpaged and
 * holds at most 500 rows, sorted by code (faculty by name). The administration screens use the permission-guarded
 * endpoints under {@code /api/v1/departments} and its siblings; these only read.
 */
@RestController
@RequestMapping("/api/v1/directory")
class DirectoryController {

	private final DirectoryService directory;

	DirectoryController(DirectoryService directory) {
		this.directory = directory;
	}

	/** At most 500 rows. */
	@GetMapping("/departments")
	List<DirectoryDepartmentResponse> departments() {
		return this.directory.departments()
			.stream()
			.map((department) -> new DirectoryDepartmentResponse(department.id(), department.code(), department.name()))
			.toList();
	}

	/** At most 500 rows. */
	@GetMapping("/divisions")
	List<DirectoryDivisionResponse> divisions() {
		return this.directory.divisions()
			.stream()
			.map((division) -> new DirectoryDivisionResponse(division.id(), division.code(), division.name(),
					division.departmentId()))
			.toList();
	}

	/**
	 * At most 500 rows.
	 *
	 * @param division only the batches of this division
	 */
	@GetMapping("/batches")
	List<DirectoryBatchResponse> batches(@RequestParam(required = false) UUID division) {
		return this.directory.batches(division)
			.stream()
			.map((batch) -> new DirectoryBatchResponse(batch.id(), batch.code(), batch.name(), batch.divisionId()))
			.toList();
	}

	/** At most 500 rows. */
	@GetMapping("/rooms")
	List<DirectoryRoomResponse> rooms() {
		return this.directory.rooms()
			.stream()
			.map((room) -> new DirectoryRoomResponse(room.id(), room.code(), room.name(), room.roomType(), room.capacity()))
			.toList();
	}

	/**
	 * At most 500 rows.
	 *
	 * @param department only the faculty of this department
	 */
	@GetMapping("/faculties")
	List<DirectoryFacultyResponse> faculties(@RequestParam(required = false) UUID department) {
		return this.directory.faculties(department).stream().map(DirectoryController::toResponse).toList();
	}

	/** The faculty records linked to the caller's account and what they teach. */
	@GetMapping("/me")
	MyCampusResponse me() {
		MyCampus campus = this.directory.myCampus();
		return new MyCampusResponse(campus.faculty().stream().map(DirectoryController::toResponse).toList(),
				campus.assignments()
					.stream()
					.map((assignment) -> new MyAssignmentResponse(assignment.id(), assignment.divisionId(),
							assignment.divisionCode(), assignment.batchId(), assignment.batchCode(), assignment.subjectId(),
							assignment.subjectCode(), assignment.subjectName(), assignment.facultyId(),
							assignment.sessionType()))
					.toList());
	}

	private static DirectoryFacultyResponse toResponse(FacultyRef faculty) {
		return new DirectoryFacultyResponse(faculty.id(), faculty.code(), faculty.name(), faculty.departmentId(),
				faculty.hasAccount());
	}

}
