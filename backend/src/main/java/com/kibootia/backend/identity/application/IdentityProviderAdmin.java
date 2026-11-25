package com.kibootia.backend.identity.application;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * What Kibootia asks of Keycloak to manage the user lifecycle (ADR 0025): the port in front of the Admin API. Only the
 * adapter in the {@code keycloak} package knows it is Keycloak, so another identity provider can replace it, though the
 * sign-in flow still assumes OpenID Connect.
 *
 * <p>Every method may throw {@link Unavailable} when the provider cannot be reached or refuses the call. The use cases
 * answer that with 503; the durable event handlers let it propagate, so the retry policy tries again.
 */
public interface IdentityProviderAdmin {

	/**
	 * Creates the account.
	 *
	 * @throws EmailInUse when an account with this email address exists
	 */
	UUID createUser(String email, boolean emailVerified, List<String> requiredActions);

	/** The account, or empty when the provider does not know it. */
	Optional<Account> find(UUID id);

	/** Disables or enables the account. Disabling also ends the user's sessions. */
	void setEnabled(UUID id, boolean enabled);

	/** Ends every session of the user, so their tokens stop being refreshed. */
	void endSessions(UUID id);

	/** Emails the user a link to set a password; Keycloak sends it. */
	void sendInvitation(UUID id);

	/** Emails the user a link to verify their address; Keycloak sends it. */
	void sendVerificationEmail(UUID id);

	/** Deletes the account. Does nothing when it is already gone. */
	void delete(UUID id);

	/** What the provider holds about an account that Kibootia needs to know. */
	record Account(boolean enabled, boolean emailVerified) {

	}

	/** An account with the email address exists. */
	class EmailInUse extends RuntimeException {

		public EmailInUse() {
			super("An account with this email address already exists");
		}

	}

	/** The provider could not be reached or refused the call. The message must not contain secrets. */
	class Unavailable extends RuntimeException {

		public Unavailable(String message, Throwable cause) {
			super(message, cause);
		}

		public Unavailable(String message) {
			super(message);
		}

	}

}
