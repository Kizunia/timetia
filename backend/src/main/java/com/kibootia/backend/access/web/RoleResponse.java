package com.kibootia.backend.access.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A role as the API returns it. {@code version} is what a later update must send back. {@code builtIn} is null for a role
 * an administrator created, and {@code ADMIN} or {@code USER} for the two the foundation protects; the administrator
 * role's {@code permissions} lists every declared permission, because it holds them all.
 */
public record RoleResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String description,
		@Schema(nullable = true, allowableValues = { "ADMIN", "USER" }) String builtIn,
		@Schema(nullable = true, description = "GLOBAL or a kind of place; absent for a platform role") String scopeType,
		@Schema(requiredMode = REQUIRED) List<String> permissions,
		@Schema(requiredMode = REQUIRED) int assignedUsers,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
