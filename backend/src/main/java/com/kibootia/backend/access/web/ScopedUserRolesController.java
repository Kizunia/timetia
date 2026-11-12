package com.kibootia.backend.access.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.access.application.ScopedAssignmentService;
import com.kibootia.backend.access.application.ScopedAssignmentService.NewScopedRole;
import com.kibootia.backend.access.application.ScopedAssignmentService.ScopedRole;

/**
 * The roles one user holds within scopes (Timetia ADR 0004). Putting the whole set keeps a retried request harmless;
 * adding and removing one assignment is what a screen uses, so it never overwrites changes it has not seen. The roles
 * without a scope are the ones behind {@code /api/v1/users/{id}/roles}.
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

	/** Adds one assignment (a repeat changes nothing) and returns everything the user now holds in places. */
	@PostMapping
	List<ScopedRoleResponse> add(@PathVariable UUID userId, @Valid @RequestBody AddScopedRoleRequest request) {
		return toResponses(this.assignments
			.addScopedRole(userId, new NewScopedRole(request.roleId(), request.scopeId(), request.scopeCode())));
	}

	/** Removes one assignment and returns what remains; 404 when the user does not hold it. */
	@DeleteMapping("/{roleId}")
	List<ScopedRoleResponse> remove(@PathVariable UUID userId, @PathVariable UUID roleId,
			@RequestParam(required = false) UUID scopeId) {
		return toResponses(this.assignments.removeScopedRole(userId, roleId, scopeId));
	}

	private static List<ScopedRoleResponse> toResponses(List<ScopedRole> roles) {
		return roles.stream()
			.map((role) -> new ScopedRoleResponse(role.roleId(), role.roleName(), role.scopeType(), role.scopeId(),
					role.scopeCode()))
			.toList();
	}

}
