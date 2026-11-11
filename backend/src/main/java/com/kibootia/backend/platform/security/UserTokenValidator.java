package com.kibootia.backend.platform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Rejects service-account tokens. The API serves signed-in users only (token-validation.md); machine clients such
 * as the administration client get no access. Keycloak marks a client-credentials token with a {@code client_id}
 * claim and names its user {@code service-account-<client>}.
 */
final class UserTokenValidator implements OAuth2TokenValidator<Jwt> {

	private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		String username = jwt.getClaimAsString("preferred_username");
		boolean serviceAccount = jwt.hasClaim("client_id")
				|| (username != null && username.startsWith(SERVICE_ACCOUNT_PREFIX));
		if (serviceAccount) {
			return OAuth2TokenValidatorResult
				.failure(new OAuth2Error("invalid_token", "Service-account tokens are not accepted", null));
		}
		return OAuth2TokenValidatorResult.success();
	}

}
