package com.kibootia.backend.platform.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

import com.kibootia.backend.platform.web.ProblemCodes;
import com.kibootia.backend.platform.web.ProblemFactory;

/**
 * Writes the 401 and 403 that the security filter chain produces as the same Problem Details the controller advice
 * produces (ADR 0043). Failures inside controllers, including method-security denials, are handled by the advice.
 */
final class ProblemDetailsHandlers {

	private final ProblemFactory problems;

	ProblemDetailsHandlers(ProblemFactory problems) {
		this.problems = problems;
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
		return (request, response, exception) -> this.problems.write(request, response,
				this.problems.create(HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Access is denied"));
	}

}
