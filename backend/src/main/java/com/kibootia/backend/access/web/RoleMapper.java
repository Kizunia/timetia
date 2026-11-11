package com.kibootia.backend.access.web;

import java.util.List;

import com.kibootia.backend.access.RoleSummary;
import com.kibootia.backend.access.application.NewRole;
import com.kibootia.backend.access.application.Role;
import com.kibootia.backend.access.application.RoleChanges;
import com.kibootia.backend.platform.authorization.Permission;

/** Converts between API types and application types by hand (ADR 0005). */
final class RoleMapper {

	private RoleMapper() {
	}

	static NewRole toNewRole(CreateRoleRequest request) {
		return new NewRole(request.name(), request.description(), request.permissions());
	}

	static RoleChanges toChanges(UpdateRoleRequest request) {
		return new RoleChanges(request.name(), request.description(), request.permissions(), request.version());
	}

	static RoleResponse toResponse(Role role) {
		return new RoleResponse(role.id(), role.name(), role.description(),
				(role.builtIn() != null) ? role.builtIn().name() : null, role.permissions().stream().sorted().toList(),
				role.assignedUsers(), role.version(), role.createdAt(), role.updatedAt());
	}

	static RoleSummaryResponse toResponse(RoleSummary role) {
		return new RoleSummaryResponse(role.id(), role.name(), role.builtIn());
	}

	static List<RoleSummaryResponse> toResponses(List<RoleSummary> roles) {
		return roles.stream().map(RoleMapper::toResponse).toList();
	}

	static PermissionResponse toResponse(Permission permission) {
		return new PermissionResponse(permission.name(), permission.description(), permission.grantedToUserRole());
	}

}
