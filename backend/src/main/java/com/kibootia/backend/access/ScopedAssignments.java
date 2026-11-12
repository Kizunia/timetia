package com.kibootia.backend.access;

import java.util.UUID;

/**
 * What a module that loads people in bulk (Timetia's provisioning import) needs from {@code access} to give roles within
 * a scope: turn the names a file uses into a role and a place, and add the assignment when it is missing. Both are for
 * administrators only and check that themselves, and neither can reach a platform role: only a role with a scope type
 * is ever found by name (Timetia ADR 0006).
 *
 * <p>This only ever adds. Taking an assignment away is the administrator's screen, one person at a time.
 */
public interface ScopedAssignments {

	/**
	 * Finds the role by its exact name and the place by its code, applying every rule an assignment must meet: the role has
	 * a scope type; a {@code GLOBAL} role takes no place; every other needs one, and it must exist. Never throws for a bad
	 * row; the refusal is in the result. Nothing is written.
	 *
	 * @param scopeCode the place's code as written in the file, or null or blank for a global role
	 */
	Resolution resolve(String roleName, String scopeCode);

	/**
	 * Gives the user the resolved role in the resolved place unless they already hold it there, auditing a new assignment
	 * with the role, the scope type and the place. Returns whether it added one; {@code false} means it was already held,
	 * and nothing changed. Re-checks the role and the place itself, so it does not trust a stale or forged resolution.
	 * Runs in the caller's transaction, so the assignment commits or rolls back with what the caller is doing.
	 *
	 * @throws com.kibootia.backend.platform.errors.ApiException 404 when the user does not exist, 400 when the role or
	 *         place is no longer valid
	 */
	boolean addIfMissing(UUID userId, Resolution resolution);

	/**
	 * Either a role and place ready to assign ({@link #failure} is null), or the reason a row cannot be assigned, as a code
	 * from {@link Failure} and a message for the person preparing the file.
	 */
	record Resolution(UUID roleId, String roleName, String scopeType, UUID scopeId, Failure failure, String message) {

		public static Resolution resolved(UUID roleId, String roleName, String scopeType, UUID scopeId) {
			return new Resolution(roleId, roleName, scopeType, scopeId, null, null);
		}

		public static Resolution refused(Failure failure, String message) {
			return new Resolution(null, null, null, null, failure, message);
		}

		public boolean isResolved() {
			return this.failure == null;
		}

	}

	/** Why a row cannot be assigned. The names are the {@code code} the import reports. */
	enum Failure {

		/** No role has this name. */
		UNKNOWN_ROLE,

		/** A role of this name exists but is a platform role, which a file cannot give. */
		ROLE_NOT_ASSIGNABLE,

		/** The role needs a place and the row names none. */
		SCOPE_REQUIRED,

		/** The role applies everywhere and the row names a place. */
		SCOPE_NOT_ALLOWED,

		/** The request names the place both by id and by code. Only the API can do this; a file names it by code. */
		SCOPE_AMBIGUOUS,

		/** The directory knows no place of the role's kind with this code or id. */
		UNKNOWN_SCOPE,

		/** Nothing provides places yet, so a place cannot be checked. Only global roles can be given. */
		SCOPE_UNVALIDATED

	}

}
