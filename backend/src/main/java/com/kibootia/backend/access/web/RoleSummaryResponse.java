package com.kibootia.backend.access.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A role in a user's list of roles. */
public record RoleSummaryResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(requiredMode = REQUIRED) String name,
		@Schema(requiredMode = REQUIRED) boolean builtIn) {

}
