package com.kibootia.backend.access.web;

import java.net.URI;
import java.util.List;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.access.application.RoleService;
import com.kibootia.backend.platform.web.PageQuery;
import com.kibootia.backend.platform.web.PageResponse;

/**
 * Roles and what they allow (ADR 0025). The controller only converts and delegates; the use cases decide who may do
 * what and write the audit entries.
 */
@RestController
@RequestMapping("/api/v1/roles")
class RolesController {

	/** API field name to entity property, for every field a client may sort by. */
	private static final Map<String, String> SORTABLE = Map.of("name", "name", "createdAt", "createdAt");

	private static final Sort BY_NAME = Sort.by(Sort.Direction.ASC, "name");

	private final RoleService roles;

	RolesController(RoleService roles) {
		this.roles = roles;
	}

	@GetMapping
	PageResponse<RoleResponse> list(@ParameterObject @Valid PageQuery query) {
		return PageResponse.of(this.roles.list(query.toPageable(SORTABLE, BY_NAME)), RoleMapper::toResponse);
	}

	@GetMapping("/{id}")
	RoleResponse get(@PathVariable UUID id) {
		return RoleMapper.toResponse(this.roles.get(id));
	}

	@PostMapping
	@ApiResponse(responseCode = "201", description = "The role was created; `Location` is its URL.")
	ResponseEntity<RoleResponse> create(@Valid @RequestBody CreateRoleRequest request) {
		RoleResponse created = RoleMapper.toResponse(this.roles.create(RoleMapper.toNewRole(request)));
		return ResponseEntity.created(URI.create("/api/v1/roles/" + created.id())).body(created);
	}

	@PutMapping("/{id}")
	RoleResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateRoleRequest request) {
		return RoleMapper.toResponse(this.roles.update(id, RoleMapper.toChanges(request)));
	}

	@DeleteMapping("/{id}")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void delete(@PathVariable UUID id) {
		this.roles.delete(id);
	}

}
