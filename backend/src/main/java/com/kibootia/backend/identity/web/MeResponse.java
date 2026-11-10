package com.kibootia.backend.identity.web;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * The signed-in user. {@code permissions} is for the interface to decide what to show; the backend enforces every
 * check itself. A user whose email address is not verified holds no permissions, and {@code emailVerified} says why.
 */
public record MeResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(nullable = true) String username,
		@Schema(nullable = true) String email,
		@Schema(requiredMode = REQUIRED) boolean emailVerified,
		@Schema(requiredMode = REQUIRED, description = "The name to show; never an email address.") String name,
		@Schema(requiredMode = REQUIRED, allowableValues = { "INVITED", "ACTIVE", "DISABLED" }) String status,
		@Schema(requiredMode = REQUIRED, allowableValues = { "SYSTEM", "LIGHT", "DARK" }) String theme,
		@Schema(requiredMode = REQUIRED) List<String> permissions,
		@Schema(requiredMode = REQUIRED) List<UserRoleResponse> roles,
			@Schema(nullable = true, description = "Changes whenever the avatar does; null when there is none. Use it to refresh a cached image.") UUID avatarVersion,
			@Schema(requiredMode = REQUIRED, description = "False when the files module is switched off; then there are no avatars.") boolean avatarsEnabled) {

}
