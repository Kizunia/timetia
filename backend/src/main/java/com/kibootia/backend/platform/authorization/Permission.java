package com.kibootia.backend.platform.authorization;

/**
 * A permission a module defines in code (ADR 0044). The name is {@code <module>.<resource>.<action>}, for example
 * {@code identity.users.invite}. A permission whose name ends in {@code -any} widens the one without the suffix from
 * "records I own" to "every record" (see {@link Scope}): {@code notes.notes.update} lets a caller change their own
 * notes, {@code notes.notes.update-any} lets them change anyone's.
 *
 * <p>A <strong>policy-decided</strong> permission (Timetia ADR 0004) is different: it is held through a role that
 * is assigned within a scope, and it is decided per record by a {@link ScopedResourcePolicy} from the caller's
 * grants and the record's facts. The flat checks never satisfy it ({@code @RequiresPermission}, and the permission-only
 * {@link Authorization#can(String)} and {@link Authorization#scopeFor}), and the administrator role does not hold it
 * implicitly. It can only be used by checking a record, so forgetting the check denies instead of over-allowing.
 *
 * @param name the permission name
 * @param description what it allows, shown to administrators in the role editor
 * @param grantedToUserRole whether the built-in {@code user} role holds it
 * @param allowedBeforeVerification whether a caller who has not verified their email address may still use it
 *        (ADR 0041); false for everything a module declares unless it says otherwise
 * @param policyDecided whether it is decided per record by a policy, from the caller's scoped grants
 */
public record Permission(String name, String description, boolean grantedToUserRole,
		boolean allowedBeforeVerification, boolean policyDecided) {

	public Permission {
		if (policyDecided && (grantedToUserRole || allowedBeforeVerification)) {
			throw new IllegalArgumentException("A policy-decided permission is granted only through a scoped role and "
					+ "needs a verified email address: '" + name + "'");
		}
	}

	public Permission(String name, String description, boolean grantedToUserRole, boolean allowedBeforeVerification) {
		this(name, description, grantedToUserRole, allowedBeforeVerification, false);
	}

	public Permission(String name, String description, boolean grantedToUserRole) {
		this(name, description, grantedToUserRole, false, false);
	}

	/** A permission the built-in {@code user} role holds. */
	public static Permission forEveryUser(String name, String description) {
		return new Permission(name, description, true);
	}

	/** A permission that must be granted through a role. */
	public static Permission restricted(String name, String description) {
		return new Permission(name, description, false);
	}

	/** A permission decided per record from the caller's scoped grants (Timetia ADR 0004). */
	public static Permission policyDecided(String name, String description) {
		return new Permission(name, description, false, false, true);
	}

	/** The same permission, usable by a caller who has not verified their email address yet. */
	public Permission beforeVerification() {
		return new Permission(this.name, this.description, this.grantedToUserRole, true, this.policyDecided);
	}

}
