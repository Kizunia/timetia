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
 */
public record Permission(String name, String description, boolean grantedToUserRole) {

	/** A permission the built-in {@code user} role holds. */
	public static Permission forEveryUser(String name, String description) {
		return new Permission(name, description, true);
	}

	/** A permission that must be granted through a role. */
	public static Permission restricted(String name, String description) {
		return new Permission(name, description, false);
	}

}
