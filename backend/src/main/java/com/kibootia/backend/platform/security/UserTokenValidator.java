package com.kibootia.backend.platform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Accepts only access tokens of signed-in users. The API serves signed-in users only (token-validation.md); machine
 * clients such as the administration client get no access, and neither does an ID token or a refresh token that
 * somehow carries the right audience (Timetia ADR 0005).
 *
 * <p>Keycloak gives an access token the type {@code Bearer}, an ID token {@code ID} and a refresh token {@code Refresh}
 * in the {@code typ} claim. The type is a positive check: a token with another type, or none, is refused. A
 * client-credentials token carries a {@code client_id} claim and its user is named {@code service-account-<client>}.
 */
final class UserTokenValidator implements OAuth2TokenValidator<Jwt> {

	private static final String SERVICE_ACCOUNT_PREFIX = "service-account-";

	private static final String ACCESS_TOKEN_TYPE = "Bearer";

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		if (!ACCESS_TOKEN_TYPE.equals(jwt.getClaimAsString("typ"))) {
			return OAuth2TokenValidatorResult
				.failure(new OAuth2Error("invalid_token", "Only access tokens are accepted", null));
		}
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
