package com.kibootia.backend.platform.security;

import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * The signed-in user of the current request, read from the validated token, for reporting to {@link SecurityEvents}
 * from places that have no {@code Authorization}. Empty when nobody is signed in or the subject is not a user ID.
 */
public final class CurrentActor {

	private CurrentActor() {
	}

	public static Optional<UUID> userId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token) || token.getSubject() == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(UUID.fromString(token.getSubject()));
		}
		catch (IllegalArgumentException ex) {
			return Optional.empty();
		}
	}

}
