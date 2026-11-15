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

/** Academic terms (semesters). Super Admin configuration; the service enforces the permission. */
@RestController
@RequestMapping("/api/v1/academic-terms")
class AcademicTermsController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "startDate", "startDate");

	private static final Sort BY_START = Sort.by(Sort.Direction.DESC, "startDate");

	private final CatalogService catalog;

	AcademicTermsController(CatalogService catalog) {
		this.catalog = catalog;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The term was created; `Location` is its URL.")
	ResponseEntity<TermResponse> create(@Valid @RequestBody CreateTermRequest request) {
		TermResponse created = CatalogMapper.toResponse(
				this.catalog.createTerm(request.code(), request.name(), request.startDate(), request.endDate()));
		return ResponseEntity.created(URI.create("/api/v1/academic-terms/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	TermResponse get(@PathVariable UUID id) {
		return CatalogMapper.toResponse(this.catalog.getTerm(id));
	}

	@PutMapping("/{id}")
	TermResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CatalogMapper.toResponse(this.catalog.renameTerm(id, request.version(), request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.catalog.deleteTerm(id);
	}

	/** @param q only terms whose code or name contains this text */
	@GetMapping
	PageResponse<TermResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.catalog.listTerms(q, query.toPageable(SORTABLE, BY_START)), CatalogMapper::toResponse);
	}

}
