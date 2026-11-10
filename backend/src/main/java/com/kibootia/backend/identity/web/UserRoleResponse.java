package com.kibootia.backend.identity.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A role a user holds, as shown next to the user. The role's own screens are in the access module's API. */
public record UserRoleResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) boolean builtIn) {

	static java.util.List<UserRoleResponse> of(java.util.List<com.kibootia.backend.access.RoleSummary> roles) {
		return roles.stream().map((role) -> new UserRoleResponse(role.id(), role.name(), role.builtIn())).toList();
	}

}
