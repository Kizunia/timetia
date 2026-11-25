package com.kibootia.backend.reference.web;

import java.net.URI;
import java.util.Map;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.reference.application.CampusService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * Departments: the top of the campus structure and the {@code DEPARTMENT} scope a role is assigned in. Super Admin
 * configuration (ADR 0043 conventions; the service enforces the permission). The code is set on create and never
 * changes; a {@code PUT} only renames.
 */
@RestController
@RequestMapping("/api/v1/departments")
class DepartmentsController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

	private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "code");

	private final CampusService campus;

	DepartmentsController(CampusService campus) {
		this.campus = campus;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The department was created; `Location` is its URL.")
	ResponseEntity<DepartmentResponse> create(@Valid @RequestBody CreateDepartmentRequest request) {
		DepartmentResponse created = CampusMapper.toResponse(this.campus.createDepartment(request.code(), request.name()));
		return ResponseEntity.created(URI.create("/api/v1/departments/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	DepartmentResponse get(@PathVariable UUID id) {
		return CampusMapper.toResponse(this.campus.getDepartment(id));
	}

	@PutMapping("/{id}")
	DepartmentResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CampusMapper.toResponse(this.campus.renameDepartment(id, request.version(), request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.campus.deleteDepartment(id);
	}

	/** @param q only departments whose code or name contains this text */
	@GetMapping
	PageResponse<DepartmentResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.campus.listDepartments(q, query.toPageable(SORTABLE, BY_CODE)),
				CampusMapper::toResponse);
	}

}
