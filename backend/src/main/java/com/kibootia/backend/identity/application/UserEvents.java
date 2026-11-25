package com.kibootia.backend.identity.application;

import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.stereotype.Component;

import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.identity.UserErased;
import com.kibootia.backend.platform.events.DurableEventListener;

/**
 * The reactions to what happens to a user (ADR 0031, ADR 0041): everything that must reach Keycloak. They are durable
 * handlers, so Keycloak being down delays them instead of losing them, and each is written to be run again with the
 * same effect, because delivery is at least once.
 *
 * <p>The methods are not public, so they are not use cases: they are reached only through the event, as the system, in
 * the event's tenant.
 */
@Component
class UserEvents {

	private static final Logger log = LoggerFactory.getLogger(UserEvents.class);

	private final UserProfileStore profiles;

	private final IdentityProviderAdmin keycloak;

	private final RoleAssignments roleAssignments;

	private final AuditLog auditLog;

	UserEvents(UserProfileStore profiles, IdentityProviderAdmin keycloak, RoleAssignments roleAssignments,
			AuditLog auditLog) {
		this.profiles = profiles;
		this.keycloak = keycloak;
		this.roleAssignments = roleAssignments;
		this.auditLog = auditLog;
	}

	/** An invitation, or a repeated one: Keycloak emails the set-password link. */
	@DurableEventListener
	void on(UserInvited event) {
		Optional<UserProfile> profile = this.profiles.find(event.userId());
		if (profile.isEmpty() || !profile.get().canSignIn()) {
			log.info("Not sending the invitation to {}: the user is gone or can no longer sign in", event.userId());
			return;
		}
		this.keycloak.sendInvitation(event.userId());
	}

	/** A user registered without a verified address: Keycloak sends the verification mail. */
	@DurableEventListener
	void on(VerificationEmailRequested event) {
		Optional<UserProfile> profile = this.profiles.find(event.userId());
		if (profile.isEmpty() || profile.get().deleted()) {
			return;
		}
		if (this.keycloak.find(event.userId()).map(IdentityProviderAdmin.Account::emailVerified).orElse(true)) {
			return;
		}
		this.keycloak.sendVerificationEmail(event.userId());
	}

	/**
	 * Brings the Keycloak account in line with the profile as it is now, whatever happened in between: enabled only for a
	 * user who is neither disabled nor deleted. A user who should not sign in also has their sessions ended, every time,
	 * so a repeated delivery cannot leave one open.
	 */
	@DurableEventListener
	void on(UserAccountChanged event) {
		UUID id = event.userId();
		Optional<UserProfile> profile = this.profiles.find(id);
		if (profile.isEmpty()) {
			return;
		}
		boolean shouldBeEnabled = profile.get().canSignIn();
		Optional<IdentityProviderAdmin.Account> account = this.keycloak.find(id);
		if (account.isEmpty()) {
			log.warn("Keycloak has no account {} to bring in line with its profile", id);
			return;
		}
		if (account.get().enabled() != shouldBeEnabled) {
			this.keycloak.setEnabled(id, shouldBeEnabled);
		}
		if (!shouldBeEnabled) {
			this.keycloak.endSessions(id);
		}
	}

	/**
	 * Erases the user: the Keycloak account first, then the profile and the roles. In that order, so that until the
	 * account is gone the profile still refuses the user; a failure after the first step is retried and deleting an
	 * account that is gone is harmless. The audit log keeps the user's ID only.
	 */
	@DurableEventListener
	void on(UserErased event) {
		UUID id = event.userId();
		this.keycloak.delete(id);
		this.roleAssignments.removeAll(id);
		if (this.profiles.find(id).isPresent()) {
			this.profiles.erase(id);
			this.auditLog.record(AuditEntry.of("identity.user.erased", "user", id));
		}
	}

}
