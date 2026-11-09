package com.kibootia.backend.platform.web;

import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

import com.kibootia.backend.platform.errors.ProblemCodes;

/** Picks the error code for a status Spring produced itself, when nothing more specific applies. */
final class StatusCodes {

	private StatusCodes() {
	}

	static String forStatus(HttpStatusCode status) {
		HttpStatus known = HttpStatus.resolve(status.value());
		if (known == null) {
			return status.is5xxServerError() ? ProblemCodes.INTERNAL_ERROR : ProblemCodes.BAD_REQUEST;
		}
		return switch (known) {
			case UNAUTHORIZED -> ProblemCodes.UNAUTHENTICATED;
			case FORBIDDEN -> ProblemCodes.FORBIDDEN;
			case NOT_FOUND -> ProblemCodes.NOT_FOUND;
			case METHOD_NOT_ALLOWED -> ProblemCodes.METHOD_NOT_ALLOWED;
			case NOT_ACCEPTABLE -> ProblemCodes.NOT_ACCEPTABLE;
			case UNSUPPORTED_MEDIA_TYPE -> ProblemCodes.UNSUPPORTED_MEDIA_TYPE;
			case PAYLOAD_TOO_LARGE -> ProblemCodes.PAYLOAD_TOO_LARGE;
			case CONFLICT -> ProblemCodes.CONCURRENT_MODIFICATION;
			case TOO_MANY_REQUESTS -> ProblemCodes.RATE_LIMITED;
			default -> known.is5xxServerError() ? ProblemCodes.INTERNAL_ERROR : ProblemCodes.BAD_REQUEST;
		};
	}

}
