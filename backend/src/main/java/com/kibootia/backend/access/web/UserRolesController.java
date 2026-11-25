package com.kibootia.backend.access.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.access.application.RoleAssignmentService;

/** The roles one user holds (ADR 0025). Putting the whole set keeps a retried request harmless. */
@RestController
@RequestMapping("/api/v1/users/{userId}/roles")
class UserRolesController {

	private final RoleAssignmentService assignments;

	UserRolesController(RoleAssignmentService assignments) {
		this.assignments = assignments;
	}

	@GetMapping
	List<RoleSummaryResponse> get(@PathVariable UUID userId) {
		return RoleMapper.toResponses(this.assignments.rolesOfUser(userId));
	}

	@PutMapping
	List<RoleSummaryResponse> replace(@PathVariable UUID userId, @Valid @RequestBody ReplaceUserRolesRequest request) {
		return RoleMapper.toResponses(this.assignments.replaceRolesOfUser(userId, request.roleIds()));
	}

}
