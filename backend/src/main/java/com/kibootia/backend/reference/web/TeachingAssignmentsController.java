package com.kibootia.backend.reference.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.reference.application.TeachingAssignment;
import com.kibootia.backend.reference.application.TeachingAssignmentService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * Teaching assignments: who teaches what, to which division or batch. Super Admin configuration; the service enforces the
 * permission. An assignment has no editable field, so there is no {@code PUT}; change one by deleting and recreating it.
 */
@RestController
@RequestMapping("/api/v1/teaching-assignments")
class TeachingAssignmentsController {

	private static final Map<String, String> SORTABLE = Map.of("createdAt", "createdAt");

	private static final Sort NEWEST_FIRST = Sort.by(Sort.Direction.DESC, "createdAt");

	private final TeachingAssignmentService service;

	TeachingAssignmentsController(TeachingAssignmentService service) {
		this.service = service;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The assignment was created; `Location` is its URL.")
	ResponseEntity<TeachingAssignmentResponse> create(@Valid @RequestBody CreateTeachingAssignmentRequest request) {
		TeachingAssignment created = this.service.create(request.divisionId(), request.batchId(), request.subjectId(),
				request.facultyId(), request.sessionType());
		return ResponseEntity.created(URI.create("/api/v1/teaching-assignments/" + created.id())).body(toResponse(created));
	}

	@GetMapping("/{id}")
	TeachingAssignmentResponse get(@PathVariable UUID id) {
		return toResponse(this.service.get(id));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.service.delete(id);
	}

	/**
	 * @param division only assignments of this division
	 * @param faculty only assignments taught by this faculty member
	 * @param subject only assignments of this subject
	 */
	@GetMapping
	PageResponse<TeachingAssignmentResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) UUID division, @RequestParam(required = false) UUID faculty,
			@RequestParam(required = false) UUID subject) {
		return PageResponse.of(this.service.list(division, faculty, subject, query.toPageable(SORTABLE, NEWEST_FIRST)),
				TeachingAssignmentsController::toResponse);
	}

	private static TeachingAssignmentResponse toResponse(TeachingAssignment a) {
		return new TeachingAssignmentResponse(a.id(), a.divisionId(), a.batchId(), a.subjectId(), a.facultyId(),
				a.sessionType(), a.version(), a.createdAt(), a.updatedAt());
	}

}
