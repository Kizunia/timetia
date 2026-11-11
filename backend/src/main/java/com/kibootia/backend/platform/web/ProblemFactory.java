package com.kibootia.backend.platform.web;

import java.io.IOException;
import java.net.URI;
import java.util.List;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.stereotype.Component;

import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/**
 * Builds the one error shape of the API (ADR 0043, RFC 9457): {@code type}, {@code title}, {@code status},
 * {@code detail}, {@code instance} and the Kibootia members {@code code}, {@code traceId} and {@code errors}. Both the
 * controller advice and the security filter chain use it, so a 403 from method security and a 403 from the chain look
 * the same. Error responses never carry stack traces, infrastructure identifiers, secrets or tokens.
 */
@Component
public class ProblemFactory {

	private final ObjectProvider<Tracer> tracer;

	private final JacksonJsonHttpMessageConverter converter = new JacksonJsonHttpMessageConverter();

	ProblemFactory(ObjectProvider<Tracer> tracer) {
		this.tracer = tracer;
	}

	public ProblemDetail create(HttpStatusCode status, String code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
		HttpStatus known = HttpStatus.resolve(status.value());
		if (known != null) {
			problem.setTitle(known.getReasonPhrase());
		}
		return decorate(problem, code);
	}

	public ProblemDetail validation(List<ApiFieldError> errors) {
		ProblemDetail problem = create(HttpStatus.BAD_REQUEST, ProblemCodes.VALIDATION_FAILED,
				"The request contains invalid fields.");
		problem.setTitle("Validation failed");
		return withErrors(problem, errors);
	}

	public ProblemDetail withErrors(ProblemDetail problem, List<ApiFieldError> errors) {
		if (!errors.isEmpty()) {
			problem.setProperty("errors", errors);
		}
		return problem;
	}

	/** Adds {@code code} (when absent) and {@code traceId} to a problem Spring built. */
	public ProblemDetail decorate(ProblemDetail problem, String code) {
		if (problem.getProperties() == null || !problem.getProperties().containsKey("code")) {
			problem.setProperty("code", code);
		}
		String traceId = traceId();
		if (traceId != null) {
			problem.setProperty("traceId", traceId);
		}
		return problem;
	}

	/** The request path as the problem's {@code instance}, or nothing when it is not a valid URI reference. */
	public void setInstance(ProblemDetail problem, HttpServletRequest request) {
		if (problem.getInstance() != null) {
			return;
		}
		try {
			problem.setInstance(URI.create(request.getRequestURI()));
		}
		catch (IllegalArgumentException ex) {
			// The path is attacker-controlled; leave the member out rather than fail the error response.
		}
	}

	/** Writes a problem from a servlet filter, where no controller advice runs. */
	public void write(HttpServletRequest request, HttpServletResponse response, ProblemDetail problem)
			throws IOException {
		setInstance(problem, request);
		response.setStatus(problem.getStatus());
		try {
			this.converter.write(problem, MediaType.APPLICATION_PROBLEM_JSON, new ServletServerHttpResponse(response));
		}
		catch (HttpMessageNotWritableException ex) {
			throw new IOException("Could not write the problem response", ex);
		}
	}

	private String traceId() {
		Tracer current = this.tracer.getIfAvailable();
		Span span = (current != null) ? current.currentSpan() : null;
		return (span != null) ? span.context().traceId() : null;
	}

}
