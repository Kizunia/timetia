package com.kibootia.backend.platform.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/** Accepts only tokens issued for this API: the {@code aud} claim must contain the configured audience. */
final class AudienceValidator implements OAuth2TokenValidator<Jwt> {

	private final String audience;

	AudienceValidator(String audience) {
		this.audience = audience;
	}

	@Override
	public OAuth2TokenValidatorResult validate(Jwt jwt) {
		if (jwt.getAudience() != null && jwt.getAudience().contains(this.audience)) {
			return OAuth2TokenValidatorResult.success();
		}
		return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "The token has the wrong audience", null));
	}

}
