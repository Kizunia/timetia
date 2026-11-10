package com.kibootia.backend.identity.application;

import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.kibootia.backend.access.AccessPermissions;
import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.access.RoleSummary;
import com.kibootia.backend.audit.AuditEntry;
import com.kibootia.backend.audit.AuditLog;
import com.kibootia.backend.identity.IdentityPermissions;
import com.kibootia.backend.identity.application.UserProfileStore.NewProfile;
import com.kibootia.backend.identity.application.UserProfileStore.TokenFacts;
import com.kibootia.backend.identity.application.UserProfileStore.UserFilter;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.RequiresPermission;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.events.EventContextFactory;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * What an administrator does with other people's accounts (ADR 0041). Each change is authorized, rate limited, audited
 * and written in one transaction with the event that brings Keycloak in line, so Kibootia refuses a disabled or deleted
 * user from the next request while Keycloak catches up through the durable event, with retries.
 *
 * <p>Two safeguards: nobody can change their own account here (an administrator who disables themselves has locked
 * themselves out), and the last administrator who can still sign in cannot be disabled or deleted. Those that can
 * remove an administrator run at {@code READ_COMMITTED} so the count behind the second safeguard sees what a concurrent
 * commit changed.
 */
@Service
public class UserAdministration {

	public static final String NOT_FOUND = "identity.user_not_found";

	public static final String EMAIL_IN_USE = "identity.email_in_use";

	public static final String CANNOT_CHANGE_SELF = "identity.cannot_change_self";

	public static final String NOT_INVITED = "identity.not_invited";

	public static final String NOT_DISABLED = "identity.not_disabled";

	public static final String NOT_DELETED = "identity.not_deleted";

	public static final String ERASURE_BEGUN = "identity.erasure_begun";

	private static final Logger log = LoggerFactory.getLogger(UserAdministration.class);

	/** Deliberately simple: Keycloak has the final say, and the address is only used to look the account up. */
	private static final Pattern EMAIL = Pattern.compile("[^@\\s]{1,64}@[^@\\s]{1,255}");

	private final UserProfileStore profiles;

	private final IdentityProviderAdmin keycloak;

	private final RoleAssignments roleAssignments;

	private final Authorization authorization;

	private final AuditLog auditLog;

	private final RateLimiter rateLimiter;

	private final ApplicationEventPublisher events;

	private final EventContextFactory eventContexts;

	private final ErasureStarter erasures;

	private final Clock clock;

	private final TransactionTemplate transactions;

	UserAdministration(UserProfileStore profiles, IdentityProviderAdmin keycloak, RoleAssignments roleAssignments,
			Authorization authorization, AuditLog auditLog, RateLimiter rateLimiter, ApplicationEventPublisher events,
			EventContextFactory eventContexts, ErasureStarter erasures, Clock clock,
			PlatformTransactionManager transactionManager) {
		this.profiles = profiles;
		this.keycloak = keycloak;
		this.roleAssignments = roleAssignments;
		this.authorization = authorization;
		this.auditLog = auditLog;
		this.rateLimiter = rateLimiter;
		this.events = events;
		this.eventContexts = eventContexts;
		this.erasures = erasures;
		this.clock = clock;
		this.transactions = new TransactionTemplate(transactionManager);
	}

	@Transactional(readOnly = true)
	@RequiresPermission(IdentityPermissions.VIEW_PROFILES)
	public Page<UserDetails> list(UserFilter filter, Pageable pageable) {
		Page<UserProfile> page = this.profiles.search(filter, pageable);
		Map<UUID, List<RoleSummary>> roles = this.roleAssignments.rolesOf(page.getContent().stream().map(UserProfile::id).toList());
		return page.map((profile) -> new UserDetails(profile, roles.getOrDefault(profile.id(), List.of())));
	}

	@Transactional(readOnly = true)
	@RequiresPermission(IdentityPermissions.VIEW_PROFILES)
	public UserDetails get(UUID id) {
		return details(this.profiles.find(id).orElseThrow(UserAdministration::notFound));
	}

	/**
	 * Creates the account in Keycloak, then Kibootia's profile, roles, audit entry and the event that sends the mail. The
	 * account comes first because its ID is the profile's key. If Kibootia's part fails, the account is removed again,
	 * so a failed invitation leaves nothing behind. The address is marked verified: the link in the email proves the
	 * mailbox when the user follows it.
	 */
	@RequiresPermission(IdentityPermissions.INVITE_USERS)
	public UserDetails invite(Invitation invitation) {
		this.rateLimiter.acquire(RateLimitCategory.INVITATIONS);
		String email = validEmail(invitation.email());
		Set<UUID> roleIds = (invitation.roleIds() == null) ? Set.of() : invitation.roleIds();
		if (!roleIds.isEmpty()) {
			// Refuse before an account exists rather than undo it.
			this.authorization.require(AccessPermissions.ASSIGN_ROLES);
		}
		if (this.profiles.emailInUse(email)) {
			throw ApiException.conflict(EMAIL_IN_USE, "Someone with this email address already has an account.");
		}
		UUID id = createAccount(email);
		try {
			return this.transactions.execute((status) -> {
				UserProfile created = this.profiles.insert(new NewProfile(id, UserProfile.Status.INVITED,
						new TokenFacts(email, email, null, null, true), this.clock.instant()));
				this.roleAssignments.assignDefaultRole(id);
				this.roleAssignments.assignInitialRoles(id, roleIds);
				this.auditLog.record(AuditEntry.of("identity.user.invited", "user", id));
				this.events.publishEvent(new UserInvited(id, this.eventContexts.capture()));
				return details(created);
			});
		}
		catch (RuntimeException ex) {
			removeAccountAfterFailure(id);
			throw ex;
		}
	}

	/** Sends the invitation again to a user who has not accepted it. */
	@Transactional
	@RequiresPermission(IdentityPermissions.INVITE_USERS)
	public void resendInvitation(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.INVITATIONS);
		UserProfile profile = live(id);
		if (profile.status() != UserProfile.Status.INVITED) {
			throw ApiException.conflict(NOT_INVITED, "This user has already accepted the invitation.");
		}
		this.auditLog.record(AuditEntry.of("identity.user.invitation-resent", "user", id));
		this.events.publishEvent(new UserInvited(id, this.eventContexts.capture()));
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	@RequiresPermission(IdentityPermissions.DISABLE_USERS)
	public UserDetails disable(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		UserProfile profile = liveOtherThanMe(id);
		if (profile.status() == UserProfile.Status.DISABLED) {
			return details(profile);
		}
		this.roleAssignments.ensureNotLastAdministrator(id);
		this.profiles.updateStatus(id, UserProfile.Status.DISABLED);
		this.auditLog.record(AuditEntry.of("identity.user.disabled", "user", id));
		changed(id);
		return details(this.profiles.find(id).orElseThrow());
	}

	@Transactional
	@RequiresPermission(IdentityPermissions.DISABLE_USERS)
	public UserDetails enable(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		UserProfile profile = live(id);
		if (profile.status() != UserProfile.Status.DISABLED) {
			throw ApiException.conflict(NOT_DISABLED, "This user is not disabled.");
		}
		this.profiles.updateStatus(id, UserProfile.Status.ACTIVE);
		this.auditLog.record(AuditEntry.of("identity.user.enabled", "user", id));
		changed(id);
		return details(this.profiles.find(id).orElseThrow());
	}

	@Transactional(isolation = Isolation.READ_COMMITTED)
	@RequiresPermission(IdentityPermissions.DELETE_USERS)
	public void delete(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		liveOtherThanMe(id);
		this.roleAssignments.ensureNotLastAdministrator(id);
		this.profiles.softDelete(id);
		this.auditLog.record(AuditEntry.of("identity.user.deleted", "user", id));
		changed(id);
	}

	@Transactional
	@RequiresPermission(IdentityPermissions.RESTORE_USERS)
	public UserDetails restore(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		if (!this.profiles.restore(id, this.authorization.currentUserId())) {
			throw ApiException.notFound(NOT_FOUND, "There is no deleted user with that ID to restore.");
		}
		this.auditLog.record(AuditEntry.of("identity.user.restored", "user", id));
		changed(id);
		return details(this.profiles.find(id).orElseThrow());
	}

	/**
	 * Begins erasing a deleted user, on a privacy request: Keycloak's account, the profile, the roles and what other
	 * modules hold are removed by durable handlers, which retry until they succeed. The audit log keeps only the user's
	 * ID, which shows as "deleted user" from then on.
	 */
	@Transactional
	@RequiresPermission(IdentityPermissions.ERASE_USERS)
	public void erase(UUID id) {
		this.rateLimiter.acquire(RateLimitCategory.ADMIN_ACTIONS);
		UserProfile profile = this.profiles.find(id).orElseThrow(UserAdministration::notFound);
		if (!profile.deleted()) {
			throw ApiException.conflict(NOT_DELETED, "Delete the user before erasing them.");
		}
		if (profile.erasureRequestedAt() != null) {
			throw ApiException.conflict(ERASURE_BEGUN, "Erasing this user has already begun.");
		}
		this.erasures.begin(id);
	}

	// ---------------------------------------------------------------------------------------------------------------

	private void changed(UUID id) {
		this.events.publishEvent(new UserAccountChanged(id, this.eventContexts.capture()));
	}

	/** The profile, unless it is deleted: most actions do not apply to a deleted user, who looks absent to them. */
	private UserProfile live(UUID id) {
		UserProfile profile = this.profiles.find(id).orElseThrow(UserAdministration::notFound);
		if (profile.deleted()) {
			throw notFound();
		}
		return profile;
	}

	private UserProfile liveOtherThanMe(UUID id) {
		if (id.equals(this.authorization.currentUserId())) {
			throw ApiException.conflict(CANNOT_CHANGE_SELF, "You cannot do this to your own account.");
		}
		return live(id);
	}

	private UserDetails details(UserProfile profile) {
		return new UserDetails(profile, this.roleAssignments.rolesOf(profile.id()));
	}

	private UUID createAccount(String email) {
		try {
			return this.keycloak.createUser(email, true, List.of("UPDATE_PASSWORD"));
		}
		catch (IdentityProviderAdmin.EmailInUse ex) {
			throw ApiException.conflict(EMAIL_IN_USE, "Someone with this email address already has an account.");
		}
		catch (IdentityProviderAdmin.Unavailable ex) {
			log.warn("Keycloak could not create an account for an invitation: {}", ex.getMessage());
			throw ApiException.serviceUnavailable("The invitation could not be created right now. Try again in a moment.");
		}
	}

	private void removeAccountAfterFailure(UUID id) {
		try {
			this.keycloak.delete(id);
		}
		catch (RuntimeException ex) {
			log.error("An invitation failed and Keycloak account {} could not be removed afterwards", id, ex);
		}
	}

	private static String validEmail(String email) {
		String normal = (email == null) ? "" : email.strip().toLowerCase(Locale.ROOT);
		if (!EMAIL.matcher(normal).matches()) {
			List<ApiFieldError> errors = new ArrayList<>();
			errors.add(new ApiFieldError("email", "invalid_format", "must be a valid email address"));
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.", errors);
		}
		return normal;
	}

	private static ApiException notFound() {
		return ApiException.notFound(NOT_FOUND, "There is no user with that ID.");
	}

}
