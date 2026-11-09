package com.kibootia.backend.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

/**
 * Stable machine-readable error codes, {@code <module>.<reason>} (ADR 0043). The frontend chooses its message from the
 * code; the {@code detail} text is a developer-facing default. Modules define their own codes with their own prefix.
 */
public final class ProblemCodes {

	public static final String UNAUTHENTICATED = "platform.unauthenticated";

	public static final String FORBIDDEN = "platform.forbidden";

	public static final String VALIDATION_FAILED = "platform.validation_failed";

	public static final String MALFORMED_REQUEST = "platform.malformed_request";

	public static final String BAD_REQUEST = "platform.bad_request";

	public static final String INVALID_SORT = "platform.invalid_sort";

	public static final String NOT_FOUND = "platform.not_found";

	public static final String METHOD_NOT_ALLOWED = "platform.method_not_allowed";

	public static final String NOT_ACCEPTABLE = "platform.not_acceptable";

	public static final String UNSUPPORTED_MEDIA_TYPE = "platform.unsupported_media_type";

	public static final String PAYLOAD_TOO_LARGE = "platform.payload_too_large";

	public static final String CONCURRENT_MODIFICATION = "platform.concurrent_modification";

	public static final String DUPLICATE = "platform.duplicate";

	public static final String RATE_LIMITED = "platform.rate_limited";

	public static final String INTERNAL_ERROR = "platform.internal_error";

	private ProblemCodes() {
	}

	/** The code for a status when nothing more specific applies. */
	static String forStatus(HttpStatusCode status) {
		HttpStatus known = HttpStatus.resolve(status.value());
		if (known == null) {
			return status.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST;
		}
		return switch (known) {
			case UNAUTHORIZED -> UNAUTHENTICATED;
			case FORBIDDEN -> FORBIDDEN;
			case NOT_FOUND -> NOT_FOUND;
			case METHOD_NOT_ALLOWED -> METHOD_NOT_ALLOWED;
			case NOT_ACCEPTABLE -> NOT_ACCEPTABLE;
			case UNSUPPORTED_MEDIA_TYPE -> UNSUPPORTED_MEDIA_TYPE;
			case PAYLOAD_TOO_LARGE -> PAYLOAD_TOO_LARGE;
			case CONFLICT -> CONCURRENT_MODIFICATION;
			case TOO_MANY_REQUESTS -> RATE_LIMITED;
			default -> known.is5xxServerError() ? INTERNAL_ERROR : BAD_REQUEST;
		};
	}

}
