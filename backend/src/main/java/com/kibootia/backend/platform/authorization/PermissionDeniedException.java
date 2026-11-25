package com.kibootia.backend.platform.authorization;

import org.springframework.security.access.AccessDeniedException;

/**
 * A signed-in, verified caller lacks a permission. It is an ordinary {@link AccessDeniedException}, answered 403; it
 * also carries the permission name so the refusal can be recorded without parsing a message.
 */
public class PermissionDeniedException extends AccessDeniedException {

	private final String permission;

	public PermissionDeniedException(String permission) {
		super("Not allowed: " + permission);
		this.permission = permission;
	}

	/** The permission the caller did not hold. */
	public String permission() {
		return this.permission;
	}

}
