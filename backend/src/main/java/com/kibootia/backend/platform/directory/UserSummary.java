package com.kibootia.backend.platform.directory;

import java.util.UUID;

/**
 * A user as other modules see them.
 *
 * @param id the Keycloak user ID
 * @param displayName the name to show; never an email address (ADR 0046)
 * @param state whether the user can sign in
 */
public record UserSummary(UUID id, String displayName, State state) {

	public enum State {

		/** Invited but has not set a password yet. */
		INVITED,

		/** Can sign in. */
		ACTIVE,

		/** An administrator disabled the account. */
		DISABLED,

		/** Soft-deleted; an administrator can still restore it within the retention period. */
		DELETED

	}

	/** Whether the user can use the application right now. */
	public boolean active() {
		return this.state == State.ACTIVE;
	}

}
