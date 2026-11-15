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

/** Divisions (classes), each under a department and the {@code DIVISION} scope a CR is assigned in. */
@RestController
@RequestMapping("/api/v1/divisions")
class DivisionsController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

	private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "code");

	private final CampusService campus;

	DivisionsController(CampusService campus) {
		this.campus = campus;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The division was created; `Location` is its URL.")
	ResponseEntity<DivisionResponse> create(@Valid @RequestBody CreateDivisionRequest request) {
		DivisionResponse created = CampusMapper
			.toResponse(this.campus.createDivision(request.code(), request.name(), request.departmentId()));
		return ResponseEntity.created(URI.create("/api/v1/divisions/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	DivisionResponse get(@PathVariable UUID id) {
		return CampusMapper.toResponse(this.campus.getDivision(id));
	}

	@PutMapping("/{id}")
	DivisionResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CampusMapper.toResponse(this.campus.renameDivision(id, request.version(), request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.campus.deleteDivision(id);
	}

	/**
	 * @param department only divisions of this department
	 * @param q only divisions whose code or name contains this text
	 */
	@GetMapping
	PageResponse<DivisionResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) UUID department, @RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.campus.listDivisions(department, q, query.toPageable(SORTABLE, BY_CODE)),
				CampusMapper::toResponse);
	}

}
