package com.kibootia.backend.identity.application;

import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kibootia.backend.access.RoleAssignments;
import com.kibootia.backend.platform.files.StoredFiles;
import com.kibootia.backend.platform.authorization.Authorization;
import com.kibootia.backend.platform.authorization.NoAuthorization;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.ratelimit.RateLimitCategory;
import com.kibootia.backend.platform.ratelimit.RateLimiter;

/**
 * What a signed-in user does with their own account (ADR 0041). These need no permission: ownership is the rule, so
 * every active user may do them. Viewing one's own profile and asking for the verification email again also work before
 * the email address is verified; changing preferences does not.
 */
@Service
public class ProfileService {

	public static final String NOT_FOUND = "identity.user_not_found";

	public static final String ALREADY_VERIFIED = "identity.already_verified";

	private final UserProfileStore profiles;

	private final RoleAssignments roleAssignments;

	private final IdentityProviderAdmin keycloak;

	private final Authorization authorization;

	private final RateLimiter rateLimiter;

	private final ObjectProvider<StoredFiles> files;

	ProfileService(UserProfileStore profiles, RoleAssignments roleAssignments, IdentityProviderAdmin keycloak,
			Authorization authorization, RateLimiter rateLimiter, ObjectProvider<StoredFiles> files) {
		this.profiles = profiles;
		this.roleAssignments = roleAssignments;
		this.keycloak = keycloak;
		this.authorization = authorization;
		this.rateLimiter = rateLimiter;
		this.files = files;
	}

	@Transactional(readOnly = true)
	@NoAuthorization("reads the caller's own profile; allowed before the email address is verified")
	public MyProfile me() {
		UUID id = this.authorization.currentUserId();
		UserProfile profile = this.profiles.find(id).orElseThrow(ProfileService::notFound);
		boolean verified = this.authorization.isEmailVerified();
		Set<String> permissions = verified ? this.roleAssignments.permissionsOf(id) : Set.of();
		boolean filesRunning = this.files.getIfAvailable() != null;
		return new MyProfile(profile, verified, permissions, this.roleAssignments.rolesOf(id), filesRunning,
				filesRunning);
	}

	@Transactional
	public MyProfile updatePreferences(UserProfile.Theme theme) {
		this.authorization.requireVerified();
		this.profiles.updateTheme(this.authorization.currentUserId(), theme);
		return me();
	}

	/**
	 * Asks Keycloak to send the verification email again. Rate limited, because each call sends a mail. A user whose
	 * address is already verified is told so, which usually means their token is simply out of date.
	 */
	@NoAuthorization("a user asks for their own verification email; it is how an unverified user becomes verified")
	public void resendVerificationEmail() {
		UUID id = this.authorization.currentUserId();
		this.rateLimiter.acquire(RateLimitCategory.VERIFICATION_RESEND);
		try {
			IdentityProviderAdmin.Account account = this.keycloak.find(id).orElseThrow(ProfileService::notFound);
			if (account.emailVerified()) {
				throw ApiException.conflict(ALREADY_VERIFIED, "The email address is already verified.");
			}
			this.keycloak.sendVerificationEmail(id);
		}
		catch (IdentityProviderAdmin.Unavailable ex) {
			throw ApiException.serviceUnavailable("The email could not be sent right now. Try again in a moment.");
		}
	}

	private static ApiException notFound() {
		return ApiException.notFound(NOT_FOUND, "There is no such user.");
	}

}
