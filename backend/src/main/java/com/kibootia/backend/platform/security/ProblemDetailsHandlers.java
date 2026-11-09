package com.kibootia.backend.platform.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;

/**
 * Writes security failures as RFC 9457 Problem Details with the extension members of the API conventions
 * ({@code code}, {@code traceId}). Only the 401 and 403 produced by the filter chain are handled here; the general
 * error model arrives with milestone 2.
 */
final class ProblemDetailsHandlers {

	static final String UNAUTHENTICATED_CODE = "platform.unauthenticated";

	static final String FORBIDDEN_CODE = "platform.forbidden";

	private final ObjectProvider<Tracer> tracer;

	ProblemDetailsHandlers(ObjectProvider<Tracer> tracer) {
		this.tracer = tracer;
	}

	AuthenticationEntryPoint authenticationEntryPoint() {
		BearerTokenAuthenticationEntryPoint bearer = new BearerTokenAuthenticationEntryPoint();
		return (request, response, exception) -> {
			// Sets the WWW-Authenticate challenge (without echoing why validation failed), then adds the body.
			bearer.commence(request, response, exception);
			write(response, HttpStatus.UNAUTHORIZED, "Authentication is required", UNAUTHENTICATED_CODE, request);
		};
	}

	AccessDeniedHandler accessDeniedHandler() {
		return (request, response, exception) -> write(response, HttpStatus.FORBIDDEN, "Access is denied",
				FORBIDDEN_CODE, request);
	}

	private void write(HttpServletResponse response, HttpStatus status, String detail, String code,
			HttpServletRequest request) throws IOException {
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		response.setCharacterEncoding(StandardCharsets.UTF_8.name());
		StringBuilder body = new StringBuilder("{\"type\":\"about:blank\",\"title\":\"")
			.append(status.getReasonPhrase())
			.append("\",\"status\":")
			.append(status.value())
			.append(",\"detail\":\"")
			.append(detail)
			.append("\",\"instance\":\"")
			.append(escape(request.getRequestURI()))
			.append("\",\"code\":\"")
			.append(code)
			.append('"');
		String traceId = traceId();
		if (traceId != null) {
			body.append(",\"traceId\":\"").append(traceId).append('"');
		}
		body.append('}');
		response.getWriter().write(body.toString());
	}

	private String traceId() {
		Tracer current = this.tracer.getIfAvailable();
		Span span = (current != null) ? current.currentSpan() : null;
		return (span != null) ? span.context().traceId() : null;
	}

	/** The request path is attacker-controlled, so keep it from breaking out of the JSON string. */
	private static String escape(String value) {
		StringBuilder escaped = new StringBuilder(value.length());
		for (char c : value.toCharArray()) {
			if (c == '"' || c == '\\') {
				escaped.append('\\').append(c);
			}
			else if (c < 0x20) {
				escaped.append(String.format("\\u%04x", (int) c));
			}
			else {
				escaped.append(c);
			}
		}
		return escaped.toString();
	}

}
