package com.kibootia.backend.audit.web;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * An audit entry as the API returns it. People appear as an ID plus, when Kibootia still holds them, a name looked up now;
 * the log itself stores only IDs (ADR 0046). The interface shows {@code ERASED} as "deleted user".
 */
public record AuditEntryResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) Instant occurredAt,
		@Schema(requiredMode = REQUIRED) PersonResponse actor,
		@Schema(requiredMode = REQUIRED, description = "A stable code such as `access.role.assigned`.") String action,
		@Schema(requiredMode = REQUIRED) String targetType,
		@Schema(nullable = true) UUID targetId,
		@Schema(nullable = true, description = "The user acted on, when the entry is about a user.") PersonResponse target,
		@Schema(requiredMode = REQUIRED, description = "Changed fields with non-personal values.") Map<String, String> changes,
		@Schema(requiredMode = REQUIRED, description = "`success` or the code of the failure.") String outcome,
		@Schema(nullable = true) String traceId) {

	/**
	 * @param name the current name, or null for the system and for a user who was erased
	 * @param kind {@code SYSTEM}, {@code USER} or {@code ERASED}
	 */
	public record PersonResponse(
			@Schema(requiredMode = REQUIRED) UUID id,
			@Schema(nullable = true) String name,
			@Schema(requiredMode = REQUIRED, allowableValues = { "SYSTEM", "USER", "ERASED" }) String kind) {

	}

}
