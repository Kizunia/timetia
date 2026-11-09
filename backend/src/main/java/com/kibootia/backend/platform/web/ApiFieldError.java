package com.kibootia.backend.platform.web;

/**
 * One invalid field in a validation failure (ADR 0043).
 *
 * @param field the request field, for example {@code email} or {@code address.city}
 * @param code a stable code for the kind of problem, for example {@code required} or {@code invalid_format}
 * @param message a developer-facing explanation
 */
public record ApiFieldError(String field, String code, String message) {

}
