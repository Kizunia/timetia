package com.kibootia.backend.access.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A role a user holds within a scope; {@code scopeId} is absent for a role that applies everywhere, and
 * {@code scopeCode} is absent when there is no place or nothing provides the codes of places yet.
 */
public record ScopedRoleResponse(
		@Schema(requiredMode = REQUIRED) UUID roleId,
		@Schema(requiredMode = REQUIRED) String roleName,
		@Schema(requiredMode = REQUIRED) String scopeType,
		@Schema(nullable = true) UUID scopeId,
		@Schema(nullable = true) String scopeCode) {

}
