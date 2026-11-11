package com.kibootia.backend.access.web;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A permission a module declares, as the role editor lists it. */
public record PermissionResponse(
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) String description,
		@Schema(requiredMode = REQUIRED) boolean grantedToUserRole) {

}
