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

import com.kibootia.backend.reference.application.CatalogService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/** Faculty members. Super Admin configuration; the service enforces the permission. */
@RestController
@RequestMapping("/api/v1/faculties")
class FacultiesController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

	private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

	private final CatalogService catalog;

	FacultiesController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The faculty member was created; `Location` is its URL.")
	ResponseEntity<FacultyResponse> create(@Valid @RequestBody CreateFacultyRequest request) {
		FacultyResponse created = CatalogMapper
			.toResponse(this.catalog.createFaculty(request.code(), request.name(), request.departmentId(), request.userId()));
		return ResponseEntity.created(URI.create("/api/v1/faculties/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	FacultyResponse get(@PathVariable UUID id) {
		return CatalogMapper.toResponse(this.catalog.getFaculty(id));
	}

	@PutMapping("/{id}")
	FacultyResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CatalogMapper.toResponse(this.catalog.renameFaculty(id, request.version(), request.name()));
	}

	/** Links the faculty member to the account they sign in as, or removes the link with a null {@code userId}. */
	@PutMapping("/{id}/account")
	FacultyResponse linkAccount(@PathVariable UUID id, @Valid @RequestBody LinkAccountRequest request) {
		return CatalogMapper.toResponse(this.catalog.linkFacultyAccount(id, request.version(), request.userId()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.catalog.deleteFaculty(id);
	}

	/**
	 * @param department only faculty of this department
	 * @param q only faculty whose code or name contains this text
	 */
	@GetMapping
	PageResponse<FacultyResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) UUID department, @RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.catalog.listFaculty(department, q, query.toPageable(SORTABLE, BY_NAME)),
				CatalogMapper::toResponse);
	}

}
