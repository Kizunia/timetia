package com.kibootia.backend.identity.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/**
 * A user as an administrator sees them. {@code state} folds the two facts an administrator acts on into one: a deleted
 * user is {@code DELETED} whatever their status was. {@code email} is shown because the caller holds the permission to
 * see profiles; the audit log never carries it.
 */
public record UserResponse(
		@Schema(requiredMode = REQUIRED) UUID id,
		@Schema(nullable = true) String username,
		@Schema(nullable = true) String email,
		@Schema(requiredMode = REQUIRED, description = "The name to show; never an email address.") String name,
		@Schema(requiredMode = REQUIRED, allowableValues = { "INVITED", "ACTIVE", "DISABLED", "DELETED" }) String state,
		@Schema(requiredMode = REQUIRED) boolean emailVerified,
		@Schema(requiredMode = REQUIRED, description = "True once erasing a deleted user has begun.") boolean erasureBegun,
		@Schema(requiredMode = REQUIRED) List<UserRoleResponse> roles,
			@Schema(nullable = true, description = "Changes whenever the avatar does; null when there is none.") UUID avatarVersion,
		@Schema(requiredMode = REQUIRED) long version,
		@Schema(requiredMode = REQUIRED) Instant createdAt,
		@Schema(requiredMode = REQUIRED) Instant updatedAt) {

}
