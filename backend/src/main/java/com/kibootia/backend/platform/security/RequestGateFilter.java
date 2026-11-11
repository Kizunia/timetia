package com.kibootia.backend.platform.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.filter.OncePerRequestFilter;

import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ProblemCodes;
import com.kibootia.backend.platform.web.ProblemFactory;

/**
 * Asks the {@link RequestGate} to admit an authenticated request. It runs after {@code TenantContextFilter}, so the
 * gate works in the caller's tenant. A refusal, and a failure of the gate itself, are answered here as Problem Details,
 * because no controller advice runs for a filter.
 */
final class RequestGateFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestGateFilter.class);

	private final RequestGate gate;

	private final ProblemFactory problems;

	private final SecurityEvents events;

	RequestGateFilter(RequestGate gate, ProblemFactory problems, SecurityEvents events) {
		this.gate = gate;
		this.problems = problems;
		this.events = events;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null || !(authentication.getPrincipal() instanceof Jwt token)) {
			chain.doFilter(request, response);
			return;
		}
		try {
			this.gate.admit(token);
		}
		catch (ApiException refusal) {
			if (refusal.status() == HttpStatus.FORBIDDEN) {
				// A known user was refused (a disabled or deleted account). A 401 or 503 says nothing about a user.
				CurrentActor.userId().ifPresent((actor) -> this.events.accountRefused(actor, refusal.code()));
			}
			ProblemDetail problem = this.problems.create(refusal.status(), refusal.code(), refusal.getMessage());
			this.problems.withErrors(problem, refusal.errors());
			refusal.headers().forEach((name, values) -> values.forEach((value) -> response.addHeader(name, value)));
			this.problems.write(request, response, problem);
			return;
		}
		catch (RuntimeException failure) {
			// The cause is logged with the trace ID; the caller learns only that something went wrong.
			log.error("The request gate failed for {} {}", request.getMethod(), request.getRequestURI(), failure);
			this.problems.write(request, response, this.problems.create(HttpStatus.INTERNAL_SERVER_ERROR,
					ProblemCodes.INTERNAL_ERROR, "An unexpected error occurred. Quote the trace ID when reporting it."));
			return;
		}
		chain.doFilter(request, response);
	}

}
