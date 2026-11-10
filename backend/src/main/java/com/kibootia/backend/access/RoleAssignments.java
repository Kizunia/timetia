package com.kibootia.backend.access;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What the {@code identity} module needs from {@code access} when it manages an account's life (ADR 0039, ADR 0041).
 * The module's own screens use the use cases behind {@code /api/v1/roles}; this contract is for the steps of the user
 * lifecycle, which {@code identity} has already authorized.
 *
 * <p>Methods that write need the caller's transaction ({@code MANDATORY}), so a role change and the user change it
 * belongs to commit or roll back together.
 */
public interface RoleAssignments {

	/** Every permission the user holds through their roles; for the administrator role, every declared permission. */
	Set<String> permissionsOf(UUID userId);

	/** The user's roles, built-in ones first. */
	List<RoleSummary> rolesOf(UUID userId);

	/** The roles of several users at once, for a list screen. A user with no role is absent from the map. */
	Map<UUID, List<RoleSummary>> rolesOf(Collection<UUID> userIds);

	/** Gives a new profile the built-in {@code user} role. Does nothing when the user already holds it. */
	void assignDefaultRole(UUID userId);

	/**
	 * Gives an invited user the roles the inviting administrator chose, after the default one. The caller needs
	 * permission to assign roles, and the administrator role may only be given by an administrator.
	 */
	void assignInitialRoles(UUID userId, Collection<UUID> roleIds);

	/**
	 * Makes the user an administrator when no administrator exists. Returns whether it did. The caller has already
	 * checked the bootstrap setting and the user's verified email address.
	 */
	boolean grantFirstAdministrator(UUID userId);

	/**
	 * Refuses with 409 {@code access.last_administrator} when the user is an administrator and the only one who can
	 * still sign in. It locks the administrator role, so two administrators cannot remove each other at once. The
	 * calling transaction must use {@code READ_COMMITTED}, so the count sees what a concurrent commit changed.
	 */
	void ensureNotLastAdministrator(UUID userId);

	/** Removes every role from the user, when the user is erased. */
	void removeAll(UUID userId);

}
