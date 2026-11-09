package com.kibootia.backend.access.builtin;

import java.util.Set;

import com.kibootia.backend.access.domain.PermissionRegistry;
import com.kibootia.backend.access.domain.PermissionSource;
import com.kibootia.backend.platform.authorization.Caller;

/**
 * Every signed-in user holds the built-in {@code user} role, whose permissions modules flag in code. This stands in for
 * the stored roles of milestone 3, which replace it behind {@link PermissionSource}. Until then no real user holds an
 * administrative permission.
 */
public final class BuiltInRolePermissionSource implements PermissionSource {

	private final PermissionRegistry registry;

	public BuiltInRolePermissionSource(PermissionRegistry registry) {
		this.registry = registry;
	}

	@Override
	public Set<String> permissionsOf(Caller caller) {
		return this.registry.userRolePermissions();
	}

}
