package com.kibootia.backend.identity.web;

import java.util.Set;

import com.kibootia.backend.identity.application.Invitation;
import com.kibootia.backend.identity.application.UserDetails;
import com.kibootia.backend.identity.application.UserProfile;

/** Converts between API types and application types by hand (ADR 0005). */
final class UserMapper {

	private UserMapper() {
	}

	static Invitation toInvitation(InviteUserRequest request) {
		return new Invitation(request.email(), (request.roleIds() != null) ? request.roleIds() : Set.of());
	}

	static UserResponse toResponse(UserDetails user) {
		UserProfile profile = user.profile();
		String state = profile.deleted() ? "DELETED" : profile.status().name();
		return new UserResponse(profile.id(), profile.username(), profile.email(), profile.displayName(), state,
				profile.emailVerified(), profile.erasureRequestedAt() != null,
				UserRoleResponse.of(user.roles()), profile.avatarFileId(),
				profile.version(), profile.createdAt(), profile.updatedAt());
	}

}
