package com.kibootia.backend.platform.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.kibootia.backend.platform.authorization.EmailNotVerifiedException;
import com.kibootia.backend.platform.authorization.PermissionDeniedException;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.web.ProblemFactory;

/**
 * Writes the 401 and 403 that the security filter chain produces as the same Problem Details the controller advice
 * produces (ADR 0043). Failures inside controllers, including method-security denials, are handled by the advice. A 403
 * of a signed-in user is reported to {@link SecurityEvents}; a 401 is not (Timetia ADR 0005).
 */
final class ProblemDetailsHandlers {

	private final ProblemFactory problems;

	private final SecurityEvents events;

	ProblemDetailsHandlers(ProblemFactory problems, SecurityEvents events) {
		this.problems = problems;
		this.events = events;
	}

	AuthenticationEntryPoint authenticationEntryPoint() {
		BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
		return (request, response, exception) -> {
			// Sets the WWW-Authenticate challenge (without echoing why validation failed), then adds the body.
			bearer.commence(request, response, exception);
			this.problems.write(request, response,
					this.problems.create(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED,
							"Authentication is required"));
		};
	}

	AccessDeniedHandler accessDeniedHandler() {
		return (request, response, exception) -> {
			boolean unverified = exception instanceof EmailNotVerifiedException;
			String permission = (exception instanceof PermissionDeniedException denied) ? denied.permission() : null;
			CurrentActor.userId()
				.ifPresent((actor) -> this.events.accessDenied(actor, permission,
						unverified ? "email_not_verified" : "forbidden"));
			this.problems.write(request, response,
					unverified
							? this.problems.create(HttpStatus.FORBIDDEN, ProblemCodes.EMAIL_NOT_VERIFIED,
									"Verify your email address to do this.")
							: this.problems.create(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Access is denied"));
		};
	}

}
