package com.kibootia.backend.access;

import java.util.Set;

/**
 * What a module needs from {@code access} to seed the roles of its own domain at startup (Timetia ADR 0004), for
 * example the Timetia roles. Called with the tenant bound and in a transaction, before any user is signed in.
 */
public interface RoleTemplates {

	/**
	 * Creates the role when no live role has this name, and never changes one that exists, so an administrator's edits
	 * to a seeded role stay. A permission that no module declares any more is left out.
	 *
	 * @param scopeType {@code GLOBAL} or a kind a module declared, or null for a platform role
	 * @return whether the role was created
	 * @throws IllegalArgumentException when the permissions do not fit the scope type: a role with a scope type holds
	 *         only policy-decided permissions, and a role that holds one needs a scope type
	 */
	boolean ensure(String name, String description, String scopeType, Set<String> permissions);

}
