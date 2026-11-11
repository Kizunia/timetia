package com.kibootia.backend.identity.web;

import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** The body of {@code PUT /api/v1/me/preferences}. */
public record UpdatePreferencesRequest(
		@NotNull @Schema(allowableValues = { "SYSTEM", "LIGHT", "DARK" }) String theme) {

}
