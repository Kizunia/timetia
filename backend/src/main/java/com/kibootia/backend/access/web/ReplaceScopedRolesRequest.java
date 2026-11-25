package com.kibootia.backend.access.web;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code PUT /api/v1/users/{id}/scoped-roles}: the roles the user should hold within scopes from now on. A
 * role that applies everywhere takes no {@code scopeId}; every other needs one. This replaces the whole set, so a screen
 * that may be out of date uses the one-assignment endpoints instead ({@link AddScopedRoleRequest}).
 */
public record ReplaceScopedRolesRequest(@NotNull @Size(max = 200) List<@NotNull @Valid ScopedRoleAssignmentRequest> assignments) {

	/** One role in one place. */
	public record ScopedRoleAssignmentRequest(@NotNull UUID roleId, UUID scopeId) {

	}

}
