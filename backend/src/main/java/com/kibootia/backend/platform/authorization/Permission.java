package com.kibootia.backend.platform.authorization;

/**
 * A permission a module defines in code (ADR 0044). The name is {@code <module>.<resource>.<action>}, for example
 * {@code identity.users.invite}. A permission whose name ends in {@code -any} widens the one without the suffix from
 * "records I own" to "every record" (see {@link Scope}): {@code notes.notes.update} lets a caller change their own
 * notes, {@code notes.notes.update-any} lets them change anyone's.
 *
 * @param name the permission name
 * @param description what it allows, shown to administrators in the role editor
 * @param grantedToUserRole whether the built-in {@code user} role holds it
 * @param allowedBeforeVerification whether a caller who has not verified their email address may still use it
 *        (ADR 0041); false for everything a module declares unless it says otherwise
 */
public record Permission(String name, String description, boolean grantedToUserRole,
		boolean allowedBeforeVerification) {

	public Permission(String name, String description, boolean grantedToUserRole) {
		this(name, description, grantedToUserRole, false);
	}

	/** A permission the built-in {@code user} role holds. */
	public static Permission forEveryUser(String name, String description) {
		return new Permission(name, description, true);
	}

	/** A permission that must be granted through a role. */
	public static Permission restricted(String name, String description) {
		return new Permission(name, description, false);
	}

	/** The same permission, usable by a caller who has not verified their email address yet. */
	public Permission beforeVerification() {
		return new Permission(this.name, this.description, this.grantedToUserRole, true);
	}

}
