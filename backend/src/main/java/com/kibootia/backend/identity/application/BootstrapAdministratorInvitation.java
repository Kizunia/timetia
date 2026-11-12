package com.kibootia.backend.identity.application;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.tenancy.TenantContext;

/**
 * Gets a fresh environment its first Super Admin without anyone touching the database or the identity provider's console
 * (Timetia ADR 0006, work package W3.3).
 *
 * <p>At startup, when {@code kibootia.identity.bootstrap-admin-email} is set and <em>nobody holds the administrator
 * role</em>, the account for that address is created and invited exactly as an invitation would be. The person is made
 * administrator when they follow the link and sign in for the first time ({@link ProfileGate}), and only while there is
 * still no administrator. Once one exists this does nothing, whatever the setting says.
 *
 * <p>It is safe to repeat. Restarting after the account exists changes nothing, with one exception: if the person has
 * <em>still not signed in</em> (for example the link expired) and there is still no administrator, the invitation is sent
 * again, so a stuck environment can be recovered with a restart and no database work.
 *
 * <p>If the identity provider cannot be reached the application still starts: the next start tries again. An address
 * the identity provider already knows (the local demo administrator, an account made by hand) is left alone, and that
 * person becomes administrator at their first sign-in through the same rule.
 *
 * <p>Turned off by {@code kibootia.identity.bootstrap-invite.enabled=false}, which the integration tests use.
 */
@Component
@ConditionalOnProperty(name = "kibootia.identity.bootstrap-invite.enabled", havingValue = "true", matchIfMissing = true)
class BootstrapAdministratorInvitation implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(BootstrapAdministratorInvitation.class);

	private final IdentityProperties properties;

	private final RoleAssignments roleAssignments;

	private final UserProfileStore profiles;

	private final UserAdministration users;

	private final Authorization authorization;

	BootstrapAdministratorInvitation(IdentityProperties properties, RoleAssignments roleAssignments,
			UserProfileStore profiles, UserAdministration users, Authorization authorization) {
		this.properties = properties;
		this.roleAssignments = roleAssignments;
		this.profiles = profiles;
		this.users = users;
		this.authorization = authorization;
	}

	@Override
	@NoAuthorization("startup work before any user is signed in")
	public void run(ApplicationArguments arguments) {
		invite();
	}

	/** Does the work of {@link #run}; public so a test can start it again as a restart would. */
	@NoAuthorization("startup work before any user is signed in")
	public void invite() {
		Optional<String> email = EmailAddresses.normalize(this.properties.bootstrapAdminEmail());
		if (email.isEmpty()) {
			if (this.properties.bootstrapAdminEmail() != null && !this.properties.bootstrapAdminEmail().isBlank()) {
				log.warn("The bootstrap administrator setting is not a valid email address, so no invitation was sent");
			}
			return;
		}
		try {
			TenantContext.runAs(TenantContext.DEFAULT_TENANT, () -> this.authorization
				.runAsSystem("invite the bootstrap administrator at startup", () -> inviteIfNeeded(email.get())));
		}
		catch (ApiException ex) {
			// 409: the identity provider already has the address. 503: it cannot be reached now. Neither stops startup.
			log.warn("The bootstrap administrator was not invited ({}); the next start tries again", ex.code());
		}
		catch (RuntimeException ex) {
			// The types only: the message of a database error can hold the values that failed.
			Throwable root = ex;
			while (root.getCause() != null && root.getCause() != root) {
				root = root.getCause();
			}
			log.warn("The bootstrap administrator was not invited ({}, caused by {}); the next start tries again",
					ex.getClass().getSimpleName(), root.getClass().getSimpleName());
		}
	}

	private void inviteIfNeeded(String email) {
		if (this.roleAssignments.hasAdministrator()) {
			return;
		}
		List<UserProfile> known = this.profiles.findByEmail(email);
		if (known.isEmpty()) {
			this.users.inviteBootstrapAdministrator(email);
			log.warn("No administrator exists, so the bootstrap administrator was invited");
		}
		else if (known.size() == 1 && known.get(0).status() == UserProfile.Status.INVITED && !known.get(0).deleted()) {
			this.users.resendBootstrapInvitation(known.get(0).id());
			log.warn("No administrator exists and the bootstrap administrator has not signed in, so the invitation was sent again");
		}
	}

}
