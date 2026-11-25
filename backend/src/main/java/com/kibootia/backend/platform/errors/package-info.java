/**
 * How application code reports a failure to the caller (ADR 0043): {@code ApiException}, field errors and the stable
 * error codes. Kept apart from {@code platform.web}, which turns them into HTTP responses, so use cases can throw them
 * without depending on the web layer.
 */
@NamedInterface("errors")
package com.kibootia.backend.platform.errors;

import org.springframework.modulith.NamedInterface;
