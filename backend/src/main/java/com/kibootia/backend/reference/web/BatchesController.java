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

/** Batches (lab groups), each under a division and the {@code BATCH} scope a BR is assigned in. */
@RestController
@RequestMapping("/api/v1/batches")
class BatchesController {

	private static final Map<String, String> SORTABLE = Map.of("code", "code", "name", "name", "createdAt", "createdAt");

	private static final Sort BY_CODE = Sort.by(Sort.Direction.ASC, "code");

	private final CampusService campus;

	BatchesController(CampusService campus) {
		this.campus = campus;
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The batch was created; `Location` is its URL.")
	ResponseEntity<BatchResponse> create(@Valid @RequestBody CreateBatchRequest request) {
		BatchResponse created = CampusMapper
			.toResponse(this.campus.createBatch(request.code(), request.name(), request.divisionId()));
		return ResponseEntity.created(URI.create("/api/v1/batches/" + created.id())).body(created);
	}

	@GetMapping("/{id}")
	BatchResponse get(@PathVariable UUID id) {
		return CampusMapper.toResponse(this.campus.getBatch(id));
	}

	@PutMapping("/{id}")
	BatchResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
		return CampusMapper.toResponse(this.campus.renameBatch(id, request.version(), request.name()));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.campus.deleteBatch(id);
	}

	/**
	 * @param division only batches of this division
	 * @param q only batches whose code or name contains this text
	 */
	@GetMapping
	PageResponse<BatchResponse> list(@ParameterObject @Valid PageQuery query,
			@RequestParam(required = false) UUID division, @RequestParam(required = false) @Size(max = 100) String q) {
		return PageResponse.of(this.campus.listBatches(division, q, query.toPageable(SORTABLE, BY_CODE)),
				CampusMapper::toResponse);
	}

}
