package com.kibootia.backend.platform.errors;

import java.time.Duration;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

/**
 * A failure a module reports to the caller with a chosen status and code (ADR 0043). Application code throws it; the
 * web layer turns it into Problem Details. The message is shown to the caller, so it must not contain secrets,
 * personal data or infrastructure detail.
 */
public class ApiException extends RuntimeException {

	private final HttpStatus status;

	private final String code;

	private final List<ApiFieldError> errors;

	private final HttpHeaders headers;

	public ApiException(HttpStatus status, String code, String message) {
		this(status, code, message, List.of(), new HttpHeaders());
	}

	private ApiException(HttpStatus status, String code, String message, List<ApiFieldError> errors,
			HttpHeaders headers) {
		super(message);
		this.status = status;
		this.code = code;
		this.errors = List.copyOf(errors);
		this.headers = headers;
	}

	/** 400 with field errors. */
	public static ApiException invalid(String code, String message, List<ApiFieldError> errors) {
		return new ApiException(HttpStatus.BAD_REQUEST, code, message, errors, new HttpHeaders());
	}

	/** 404. Also the answer when the record exists but the caller may not see it, so existence is not revealed. */
	public static ApiException notFound(String code, String message) {
		return new ApiException(HttpStatus.NOT_FOUND, code, message);
	}

	/** 409 for a duplicate or a rule about the current state, such as removing the last administrator. */
	public static ApiException conflict(String code, String message) {
		return new ApiException(HttpStatus.CONFLICT, code, message);
	}

	/** 413. */
	public static ApiException payloadTooLarge(String code, String message) {
		return new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, code, message);
	}

	/** 415. */
	public static ApiException unsupportedMediaType(String code, String message) {
		return new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, code, message);
	}

	/** 429 with a {@code Retry-After} header. */
	public static ApiException tooManyRequests(String message, Duration retryAfter) {
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, retryAfter.toSeconds())));
		return new ApiException(HttpStatus.TOO_MANY_REQUESTS, ProblemCodes.RATE_LIMITED, message, List.of(), headers);
	}

	/** 503 when a system this call needs, such as Keycloak, cannot be reached. The caller may try again later. */
	public static ApiException serviceUnavailable(String message) {
		return new ApiException(HttpStatus.SERVICE_UNAVAILABLE, ProblemCodes.SERVICE_UNAVAILABLE, message);
	}

	public HttpStatus status() {
		return this.status;
	}

	public String code() {
		return this.code;
	}

	public List<ApiFieldError> errors() {
		return this.errors;
	}

	public HttpHeaders headers() {
		return this.headers;
	}

}
