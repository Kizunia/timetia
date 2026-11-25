package com.kibootia.backend.identity.application;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.identity.application.UserProfileStore.NewProfile;
import com.kibootia.backend.identity.application.UserProfileStore.TokenFacts;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.platform.security.RequestGate;

/**
 * Runs before every authenticated API request (ADR 0041). It is the one place that turns "a valid token" into "a user
 * Kibootia knows":
 *
 * <ul>
 * <li>a user seen for the first time gets a profile, the built-in {@code user} role and, when their verified email
 * address is the bootstrap one and nobody is an administrator yet, the administrator role;</li>
 * <li>a user already known has name and email refreshed from the token, once per token;</li>
 * <li>a user who was disabled or deleted is refused with 403, from the very next request, whatever their token says.</li>
 * </ul>
 *
 * <p>A user it has not seen is first checked against Keycloak. Without that, an old token of a user who was just erased
 * would create the profile again.
 */
@Component
class ProfileGate implements RequestGate {

	/** 403 for a user an administrator disabled or deleted. */
	public static final String ACCOUNT_DISABLED = "identity.account_disabled";

	private static final Logger log = LoggerFactory.getLogger(ProfileGate.class);

	private final UserProfileStore profiles;

	private final IdentityProviderAdmin keycloak;

	private final RoleAssignments roleAssignments;

	private final AuditLog auditLog;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	private final IdentityProperties properties;

	private final TransactionTemplate transactions;

	ProfileGate(UserProfileStore profiles, IdentityProviderAdmin keycloak, RoleAssignments roleAssignments,
			AuditLog auditLog, ApplicationEventPublisher events, EventContextFactory eventContexts,
			IdentityProperties properties, PlatformTransactionManager transactionManager) {
		this.profiles = profiles;
		this.keycloak = keycloak;
		this.roleAssignments = roleAssignments;
		this.auditLog = auditLog;
		this.events = events;
		this.eventContexts = eventContexts;
		this.properties = properties;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Override
	@com.kibootia.backend.platform.authorization.NoAuthorization("runs before any use case, to know who is calling")
	public void admit(Jwt token) {
		UUID userId = userId(token);
		TokenFacts facts = new TokenFacts(token.getClaimAsString("preferred_username"), token.getClaimAsString("email"),
				token.getClaimAsString("given_name"), token.getClaimAsString("family_name"),
				Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")));
		Instant issuedAt = (token.getIssuedAt() != null) ? token.getIssuedAt() : Instant.now();

		UserProfile profile = this.profiles.find(userId).orElse(null);
		if (profile == null) {
			profile = provision(userId, facts, issuedAt);
		}
		if (!profile.canSignIn()) {
			throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_DISABLED, "This account is disabled.");
		}
		boolean justVerified = !profile.emailVerified() && facts.emailVerified();
		// An invited user's first sign-in: following the link in the invitation email proves they own the address (Timetia
		// ADR 0006). The refresh below turns INVITED into ACTIVE, so this must be read before it.
		boolean firstSignInAfterInvitation = profile.status() == UserProfile.Status.INVITED;
		refresh(profile, facts, issuedAt);
		if ((justVerified || firstSignInAfterInvitation) && isBootstrapAdministrator(facts)) {
			// Someone who registered with the bootstrap address before verifying it is made administrator the moment
			// they verify it: until then the token proved nothing about who owns the address. The bootstrap invitation
			// is the same: the person is made administrator when they first sign in with the verified address, and only
			// while nobody is an administrator yet (grantFirstAdministrator decides that under a lock).
			this.transactions.executeWithoutResult((status) -> grantBootstrapAdministrator(userId));
		}
	}

	private UserProfile provision(UUID userId, TokenFacts facts, Instant issuedAt) {
		IdentityProviderAdmin.Account account = knownToKeycloak(userId);
		try {
			return this.transactions.execute((status) -> create(userId, facts, issuedAt, account));
		}
		catch (DataIntegrityViolationException ex) {
			// Another request of the same user created the profile first; use theirs.
			return this.profiles.find(userId).orElseThrow(() -> ex);
		}
	}

	private IdentityProviderAdmin.Account knownToKeycloak(UUID userId) {
		try {
			IdentityProviderAdmin.Account account = this.keycloak.find(userId)
				.orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED,
						"This account no longer exists."));
			if (!account.enabled()) {
				throw new ApiException(HttpStatus.FORBIDDEN, ACCOUNT_DISABLED, "This account is disabled.");
			}
			return account;
		}
		catch (IdentityProviderAdmin.Unavailable ex) {
			log.warn("Keycloak could not confirm a new user: {}", ex.getMessage());
			throw ApiException.serviceUnavailable("Sign-in cannot be completed right now. Try again in a moment.");
		}
	}

	private UserProfile create(UUID userId, TokenFacts facts, Instant issuedAt, IdentityProviderAdmin.Account account) {
		UserProfile created = this.profiles
			.insert(new NewProfile(userId, UserProfile.Status.ACTIVE, facts, issuedAt));
		this.roleAssignments.assignDefaultRole(userId);
		if (isBootstrapAdministrator(facts)) {
			grantBootstrapAdministrator(userId);
		}
		if (!facts.emailVerified() && !account.emailVerified()) {
			this.events.publishEvent(new VerificationEmailRequested(userId, this.eventContexts.capture()));
		}
		return created;
	}

	/** In the caller's transaction. Does nothing once an administrator exists. */
	private void grantBootstrapAdministrator(UUID userId) {
		if (this.roleAssignments.grantFirstAdministrator(userId)) {
			this.auditLog.record(AuditEntry.of("identity.bootstrap-admin.granted", "user", userId));
			log.warn("The bootstrap setting made user {} the first administrator", userId);
		}
	}

	/** The setting names an address; the token must carry it, verified, so registering with it first proves nothing. */
	private boolean isBootstrapAdministrator(TokenFacts facts) {
		String bootstrap = this.properties.bootstrapAdminEmail();
		return bootstrap != null && !bootstrap.isBlank() && facts.emailVerified() && facts.email() != null
				&& facts.email().strip().equalsIgnoreCase(bootstrap.strip());
	}

	/** Copies the token's facts into the profile when the token is newer; a lost race is retried by the next request. */
	private void refresh(UserProfile profile, TokenFacts facts, Instant issuedAt) {
		try {
			this.transactions.executeWithoutResult((status) -> this.profiles.refresh(profile.id(), facts, issuedAt));
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			log.debug("The profile of {} changed while refreshing it; the next request refreshes it", profile.id());
		}
	}

	private static UUID userId(Jwt token) {
		try {
			return UUID.fromString(token.getSubject());
		}
		catch (IllegalArgumentException | NullPointerException ex) {
			throw new ApiException(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED,
					"The token does not identify a user.");
		}
	}

}
