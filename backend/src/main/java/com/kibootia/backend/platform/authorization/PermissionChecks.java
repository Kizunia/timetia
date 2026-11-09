package com.kibootia.backend.platform.authorization;

import org.springframework.stereotype.Component;

/**
 * The bridge between {@link RequiresPermission} and Spring Security's method security: the annotation's expression calls
 * {@link #holds}, which asks the decision point. Public only because the expression language needs to call it; modules
 * use {@link Authorization}.
 */
@Component("authorizationChecks")
public class PermissionChecks {

	private final Authorization authorization;

	PermissionChecks(Authorization authorization) {
		this.authorization = authorization;
	}

	public boolean holds(String permission) {
		return this.authorization.can(permission);
	}

}
