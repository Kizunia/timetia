package com.kibootia.backend.access;

import java.util.List;

import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.authorization.Permission;
import com.kibootia.backend.platform.authorization.PermissionProvider;

/**
 * The permissions of the access module (ADR 0044). None is held by the built-in {@code user} role; the administrator
 * role holds every permission.
 */
@Component
public class AccessPermissions implements PermissionProvider {

	/** See the roles, what they allow, and which roles a user holds. */
	public static final String VIEW_ROLES = "access.roles.view";

	/** Create, change and delete roles. A role can only be given permissions its editor holds. */
	public static final String MANAGE_ROLES = "access.roles.manage";

	/** Give users roles and take them away. Only an administrator can give the administrator role. */
	public static final String ASSIGN_ROLES = "access.roles.assign";

	@Override
	public List<Permission> permissions() {
		return List.of(Permission.restricted(VIEW_ROLES, "View roles, their permissions and who holds them"),
				Permission.restricted(MANAGE_ROLES, "Create, change and delete roles"),
				Permission.restricted(ASSIGN_ROLES, "Give users roles and take them away"));
	}

}
