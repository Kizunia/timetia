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

/** Subjects (courses). Super Admin configuration; the service enforces the permission. */
@RestController
@RequestMapping("/api/v1/subjects")
class SubjectsController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

	private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "code");

	private final CatalogService catalog;

	SubjectsController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The subject was created; `Location` is its URL.")
	ResponseEntity<SubjectResponse> create(@Valid @RequestBody CreateSubjectRequest request) {
		SubjectResponse created = CatalogMapper.toResponse(this.catalog.createSubject(request.code(), request.name()));
		return ResponseEntity.created(URI.create("/api/v1/subjects/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	SubjectResponse get(@PathVariable UUID id) {
		return CatalogMapper.toResponse(this.catalog.getSubject(id));
	}

	@PutMapping("/{id}")
	SubjectResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CatalogMapper.toResponse(this.catalog.renameSubject(id, request.version(), request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.catalog.deleteSubject(id);
	}

	/** @param q only subjects whose code or name contains this text */
	@GetMapping
	PageResponse<SubjectResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.catalog.listSubjects(q, query.toPageable(SORTABLE, BY_CODE)), CatalogMapper::toResponse);
	}

}
