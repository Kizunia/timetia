package com.kibootia.backend.platform.errors;

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

}
