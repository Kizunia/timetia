package com.kibootia.backend.platform.security;

import java.util.UUID;

/**
 * Where the platform reports refusals of authenticated callers, so they can be recorded (Timetia ADR 0005). The
 * {@code audit} module implements it. Without an implementation nothing is recorded.
 *
 * <p>Only the user ID, a permission name or a code is passed: never a token, a name or an email address. Reporting
 * must never change the answer to the caller, so an implementation swallows its own failures. An unauthenticated
 * request (a 401) is not reported here: an unknown caller could otherwise make the application write without limit.
 */
public interface SecurityEvents {

	/** Reports nothing. Used when no implementation is present. */
	SecurityEvents NONE = new SecurityEvents() {
		@Override
		public void accessDenied(UUID actor, String permission, String reason) {
		}

		@Override
		public void accountRefused(UUID actor, String reason) {
		}

		@Override
		public void rateLimited(UUID actor, String category) {
		}
	};

	/**
	 * A signed-in user was refused an action.
	 *
	 * @param permission the permission that was missing, or null when the refusal did not name one
	 * @param reason {@code forbidden} or {@code email_not_verified}
	 */
	void accessDenied(UUID actor, String permission, String reason);

	/** A signed-in user's account was refused at the request gate, for example because it is disabled. */
	void accountRefused(UUID actor, String reason);

	/** A user used up the allowance of a rate-limited category. */
	void rateLimited(UUID actor, String category);

}
