package com.kibootia.backend.platform.security;

import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Runs once per authenticated API request, after the token is validated and the tenant is bound, before any controller
 * (ADR 0041, ADR 0044). The {@code identity} module implements it: it creates the user's profile on first use, refreshes
 * the read copy of name and email, and refuses a user who was disabled or deleted. Without an implementation every
 * authenticated request passes, which is what a deployment without the {@code identity} module wants.
 *
 * <p>It is a port, not a Spring Security component, so the module that owns the profile decides what a refusal is and
 * the platform only turns it into a response.
 */
public interface RequestGate {

	/**
	 * Lets the request through by returning, or refuses it by throwing an
	 * {@link com.kibootia.backend.platform.errors.ApiException}.
	 */
	void admit(Jwt token);

}
