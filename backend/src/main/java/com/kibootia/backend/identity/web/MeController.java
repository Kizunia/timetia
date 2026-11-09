package com.kibootia.backend.identity.web;

import io.swagger.v3.oas.annotations.media.Schema;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * Returns the identity of the signed-in user, read from the validated access token. Milestone 1 adds no profile
 * data; permissions and the profile are added when the access module exists (ADR 0025).
 */
@RestController
class MeController {

	@GetMapping("/api/v1/me")
	MeResponse me(@AuthenticationPrincipal Jwt token) {
		return new MeResponse(token.getSubject(), token.getClaimAsString("preferred_username"),
				token.getClaimAsString("email"), Boolean.TRUE.equals(token.getClaimAsBoolean("email_verified")),
				token.getClaimAsString("name"));
	}

	record MeResponse(
			@Schema(requiredMode = REQUIRED) String id,
			@Schema(nullable = true) String username,
			@Schema(nullable = true) String email,
			@Schema(requiredMode = REQUIRED) boolean emailVerified,
			@Schema(nullable = true) String name) {

	}

}
