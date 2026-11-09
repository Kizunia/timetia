package com.kibootia.backend.platform.web;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Turns every failure into the Problem Details shape of ADR 0043 and the status table of the API conventions. Spring's
 * own exceptions (wrong method, unreadable body, unknown path) pass through {@link #handleExceptionInternal} so they get
 * a {@code code} and a {@code traceId} too. Unexpected failures answer 500 with a generic message; the cause is logged
 * with the same trace ID and never sent to the caller.
 */
@RestControllerAdvice
class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** MySQL's error number for a duplicate key. */
	private static final int MYSQL_DUPLICATE_ENTRY = 1062;

	private final ProblemFactory problems;

	GlobalExceptionHandler(ProblemFactory problems) {
		this.problems = problems;
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ResponseEntity<Object> response = super.handleExceptionInternal(ex, body, headers, statusCode, request);
		if (response != null && response.getBody() instanceof ProblemDetail problem) {
			this.problems.decorate(problem, ProblemCodes.forStatus(statusCode));
			this.problems.setInstance(problem, ((ServletWebRequest) request).getRequest());
		}
		return response;
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ApiFieldError> errors = new ArrayList<>();
		for (FieldError error : ex.getBindingResult().getFieldErrors()) {
			errors.add(fieldError(error.getField(), error.getCode(), error.getDefaultMessage()));
		}
		for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
			errors.add(fieldError(error.getObjectName(), error.getCode(), error.getDefaultMessage()));
		}
		return handleExceptionInternal(ex, this.problems.validation(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<ApiFieldError> errors = new ArrayList<>();
		for (ParameterValidationResult result : ex.getParameterValidationResults()) {
			if (result instanceof ParameterErrors parameterErrors) {
				for (FieldError error : parameterErrors.getFieldErrors()) {
					errors.add(fieldError(error.getField(), error.getCode(), error.getDefaultMessage()));
				}
			}
			else {
				String name = result.getMethodParameter().getParameterName();
				for (MessageSourceResolvable error : result.getResolvableErrors()) {
					String[] codes = error.getCodes();
					String constraint = (codes != null && codes.length > 0) ? codes[codes.length - 1] : null;
					errors.add(fieldError((name != null) ? name : "parameter", constraint,
							error.getDefaultMessage()));
				}
			}
		}
		return handleExceptionInternal(ex, this.problems.validation(errors), headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		// The parser's message can quote the input or name internal classes, so it is not passed on.
		ProblemDetail problem = this.problems.create(HttpStatus.BAD_REQUEST, ProblemCodes.MALFORMED_REQUEST,
				"The request body could not be read.");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.BAD_REQUEST, request);
	}

	@Override
	protected ResponseEntity<Object> handleMaxUploadSizeExceededException(MaxUploadSizeExceededException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		ProblemDetail problem = this.problems.create(HttpStatus.PAYLOAD_TOO_LARGE, ProblemCodes.PAYLOAD_TOO_LARGE,
				"The upload is larger than the allowed size.");
		return handleExceptionInternal(ex, problem, headers, HttpStatus.PAYLOAD_TOO_LARGE, request);
	}

	@ExceptionHandler(ApiException.class)
	ResponseEntity<Object> handleApiException(ApiException ex, HttpServletRequest request) {
		ProblemDetail problem = this.problems.create(ex.status(), ex.code(), ex.getMessage());
		this.problems.withErrors(problem, ex.errors());
		this.problems.setInstance(problem, request);
		return respond(problem, ex.headers());
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<Object> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
		// Raised by method security, after the filter chain; the filter chain's own 403 has the same shape.
		return respond(request, HttpStatus.FORBIDDEN, ProblemCodes.FORBIDDEN, "Access is denied");
	}

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<Object> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
		ProblemDetail problem = this.problems.create(HttpStatus.UNAUTHORIZED, ProblemCodes.UNAUTHENTICATED,
				"Authentication is required");
		this.problems.setInstance(problem, request);
		HttpHeaders headers = new HttpHeaders();
		headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
		return respond(problem, headers);
	}

	@ExceptionHandler(OptimisticLockingFailureException.class)
	ResponseEntity<Object> handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
		return respond(request, HttpStatus.CONFLICT, ProblemCodes.CONCURRENT_MODIFICATION,
				"The record was changed by someone else. Reload it and try again.");
	}

	@ExceptionHandler(DataIntegrityViolationException.class)
	ResponseEntity<Object> handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
		if (isDuplicateKey(ex)) {
			return respond(request, HttpStatus.CONFLICT, ProblemCodes.DUPLICATE,
					"A record with the same unique values already exists.");
		}
		// Any other integrity failure (a missing required column, a broken reference) is a defect, not a client error.
		return handleUnexpected(ex, request);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<Object> handleUnexpected(Exception ex, HttpServletRequest request) {
		log.error("Unhandled exception for {} {}", request.getMethod(), request.getRequestURI(), ex);
		return respond(request, HttpStatus.INTERNAL_SERVER_ERROR, ProblemCodes.INTERNAL_ERROR,
				"An unexpected error occurred. Quote the trace ID when reporting it.");
	}

	private ResponseEntity<Object> respond(HttpServletRequest request, HttpStatus status, String code, String detail) {
		ProblemDetail problem = this.problems.create(status, code, detail);
		this.problems.setInstance(problem, request);
		return respond(problem, new HttpHeaders());
	}

	private static ResponseEntity<Object> respond(ProblemDetail problem, HttpHeaders headers) {
		return ResponseEntity.status(problem.getStatus())
			.headers(headers)
			.contentType(MediaType.APPLICATION_PROBLEM_JSON)
			.body(problem);
	}

	private static boolean isDuplicateKey(Throwable failure) {
		for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
			if (cause instanceof SQLException sql && sql.getErrorCode() == MYSQL_DUPLICATE_ENTRY) {
				return true;
			}
			if (cause.getCause() == cause) {
				break;
			}
		}
		return false;
	}

	private static ApiFieldError fieldError(String field, String constraint, String message) {
		String code = fieldCode(constraint);
		// A conversion failure's message quotes Java type names, which the caller has no use for.
		String text = "invalid_type".equals(code) ? "has the wrong type" : message;
		return new ApiFieldError(field, code, text);
	}

	/** Maps a Bean Validation constraint name to the stable field code the frontend relies on. */
	static String fieldCode(String constraint) {
		if (constraint == null) {
			return "invalid";
		}
		return switch (constraint) {
			case "NotNull", "NotBlank", "NotEmpty" -> "required";
			case "Email", "Pattern" -> "invalid_format";
			case "Size", "Length" -> "invalid_size";
			case "Min", "Max", "DecimalMin", "DecimalMax", "Positive", "PositiveOrZero", "Negative",
					"NegativeOrZero", "Range" -> "out_of_range";
			case "typeMismatch" -> "invalid_type";
			default -> constraint.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase(Locale.ROOT);
		};
	}

}
