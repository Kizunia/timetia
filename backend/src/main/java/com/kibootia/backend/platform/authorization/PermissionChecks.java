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

	/**
	 * True when the caller holds the permission. A denial for a caller who has not verified their email address is
	 * raised here as {@link EmailNotVerifiedException}, so the response says why; the expression language passes a
	 * runtime exception through unchanged.
	 */
	public boolean holds(String permission) {
		if (this.authorization.can(permission)) {
			return true;
		}
		throw this.authorization.denied(permission);
	}

}
