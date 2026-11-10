package com.kibootia.backend.identity.application;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's profile as the use cases see it, apart from the table row and the API type (ADR 0004). Keycloak is the source
 * of truth for username, email and name; this is a read copy, refreshed from the token and never edited here (ADR 0041).
 * What Kibootia owns is the status, the roles (in {@code access}) and the preferences.
 *
 * @param id the Keycloak user ID, which is also the key of the row
 * @param status whether the user can sign in; independent of being deleted
 * @param deleted true while the profile is soft-deleted and an administrator can still restore it
 * @param erasureRequestedAt when erasure began, or null; the row disappears once Keycloak has deleted the account
 * @param avatarFileId the file the files module keeps as the user's avatar, or null when there is none
 */
public record UserProfile(UUID id, UUID tenantId, long version, Status status, String username, String email,
		String firstName, String lastName, boolean emailVerified, Theme theme, boolean deleted, Instant deletedAt,
		Instant erasureRequestedAt, Instant createdAt, Instant updatedAt, UUID avatarFileId) {

	/** Whether the user may sign in. A deleted user is not shown as disabled; deletion is a separate fact. */
	public enum Status {

		/** Created by an invitation; the user has not signed in yet. */
		INVITED,

		ACTIVE,

		/** An administrator disabled the account. */
		DISABLED

	}

	/** The colour scheme the user prefers. */
	public enum Theme {

		SYSTEM, LIGHT, DARK

	}

	/** The name to show: first and last name, else the username. Never an email address (ADR 0046). */
	public String displayName() {
		String full = ((this.firstName != null) ? this.firstName : "") + " " + ((this.lastName != null) ? this.lastName : "");
		full = full.strip();
		if (!full.isEmpty()) {
			return full;
		}
		// An invited user's username is their email address, which is never shown as a name.
		boolean usable = this.username != null && !this.username.isBlank() && this.username.indexOf('@') < 0;
		return usable ? this.username : "Unnamed user";
	}

	/** Whether the user can use the application right now. */
	public boolean canSignIn() {
		return !this.deleted && this.status != Status.DISABLED;
	}

}
