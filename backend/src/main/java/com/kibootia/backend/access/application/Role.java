package com.kibootia.backend.access.application;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * A role as the use cases see it, apart from the table row and the API type (ADR 0004).
 *
 * @param id the role's ID
 * @param version the optimistic-locking version; an edit must name the version it started from
 * @param name unique among live roles of the tenant
 * @param description what the role is for
 * @param builtIn which protected role this is, or null for a role an administrator created
 * @param permissions what the role allows; for the administrator role this is every declared permission, computed
 * @param assignedUsers how many users hold the role
 */
public record Role(UUID id, long version, String name, String description, BuiltIn builtIn, Set<String> permissions,
		int assignedUsers, Instant createdAt, Instant updatedAt) {

	/** The roles the foundation creates and protects. */
	public enum BuiltIn {

		/** Holds every permission, cannot be edited or deleted, and always has at least one active holder. */
		ADMIN,

		/** Given to every new profile. Its permissions can be edited; it cannot be renamed or deleted. */
		USER

	}

	public boolean isBuiltIn() {
		return this.builtIn != null;
	}

}
