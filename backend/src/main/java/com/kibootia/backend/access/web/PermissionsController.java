package com.kibootia.backend.access.web;

import java.util.Comparator;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.access.application.RoleService;
import com.kibootia.backend.platform.authorization.Permission;

/**
 * Every permission the modules declare in code, for the role editor (ADR 0044). The list is short and fixed for a
 * running application, so it is not paged.
 */
@RestController
@RequestMapping("/api/v1/permissions")
class PermissionsController {

	private final RoleService roles;

	PermissionsController(RoleService roles) {
		this.roles = roles;
	}

	@GetMapping
	List<PermissionResponse> list() {
		return this.roles.permissions()
			.stream()
			.sorted(Comparator.comparing(Permission::name))
			.map(RoleMapper::toResponse)
			.toList();
	}

}
