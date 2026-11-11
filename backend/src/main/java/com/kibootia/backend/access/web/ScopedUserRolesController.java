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

import com.kibootia.backend.access.application.ScopedAssignmentService;
import com.kibootia.backend.access.application.ScopedAssignmentService.NewScopedRole;
import com.kibootia.backend.access.application.ScopedAssignmentService.ScopedRole;

/**
 * The roles one user holds within scopes (Timetia ADR 0004). Putting the whole set keeps a retried request harmless.
 * The roles without a scope are the ones behind {@code /api/v1/users/{id}/roles}.
 */
@RestController
@RequestMapping("/api/v1/users/{userId}/scoped-roles")
class ScopedUserRolesController {

	private final ScopedAssignmentService assignments;

	ScopedUserRolesController(ScopedAssignmentService assignments) {
		this.assignments = assignments;
	}

	@GetMapping
	List<ScopedRoleResponse> get(@PathVariable UUID userId) {
		return toResponses(this.assignments.scopedRolesOf(userId));
	}

	@PutMapping
	List<ScopedRoleResponse> replace(@PathVariable UUID userId, @Valid @RequestBody ReplaceScopedRolesRequest request) {
		List<NewScopedRole> wanted = request.assignments()
			.stream()
			.map((assignment) -> new NewScopedRole(assignment.roleId(), assignment.scopeId()))
			.toList();
		return toResponses(this.assignments.replaceScopedRolesOfUser(userId, wanted));
	}

	private static List<ScopedRoleResponse> toResponses(List<ScopedRole> roles) {
		return roles.stream()
			.map((role) -> new ScopedRoleResponse(role.roleId(), role.roleName(), role.scopeType(), role.scopeId()))
			.toList();
	}

}
