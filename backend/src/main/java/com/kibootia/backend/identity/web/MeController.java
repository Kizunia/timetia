package com.kibootia.backend.identity.web;

import java.util.List;
import java.util.Locale;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kibootia.backend.identity.application.MyProfile;
import com.kibootia.backend.identity.application.ProfileService;
import com.kibootia.backend.identity.application.UserProfile;
import com.kibootia.backend.platform.errors.ApiException;
import com.kibootia.backend.platform.errors.ApiFieldError;
import com.kibootia.backend.platform.errors.ProblemCodes;

/**
 * The signed-in user's own account (ADR 0041): who they are, what they may do (for the interface's sake), their
 * preferences, and asking for the verification email again. Everything here is the caller's own, so no permission is
 * needed; the first and last work before the email address is verified.
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

	private final ProfileService profiles;

	MeController(ProfileService profiles) {
		this.profiles = profiles;
	}

	@GetMapping
	MeResponse me() {
		return toResponse(this.profiles.me());
	}

	@PutMapping("/preferences")
	MeResponse updatePreferences(@Valid @RequestBody UpdatePreferencesRequest request) {
		return toResponse(this.profiles.updatePreferences(theme(request.theme())));
	}

	/** Asks Keycloak to send the verification email again. Rate limited. */
	@PostMapping("/verification-email")
	@ResponseStatus(HttpStatus.NO_CONTENT)
	void resendVerificationEmail() {
		this.profiles.resendVerificationEmail();
	}

	private static UserProfile.Theme theme(String value) {
		try {
			return UserProfile.Theme.valueOf(value.toUpperCase(Locale.ROOT));
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.invalid(ProblemCodes.VALIDATION_FAILED, "The request contains invalid fields.",
					List.of(new ApiFieldError("theme", "invalid_value", "must be SYSTEM, LIGHT or DARK")));
		}
	}

	static MeResponse toResponse(MyProfile me) {
		UserProfile profile = me.profile();
		return new MeResponse(profile.id(), profile.username(), profile.email(), me.emailVerified(),
				profile.displayName(), profile.status().name(), profile.theme().name(),
				me.permissions().stream().sorted().toList(),
				UserRoleResponse.of(me.roles()), profile.avatarFileId(), me.avatarsEnabled());
	}

}
