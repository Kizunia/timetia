package com.kibootia.backend.identity.application;

import java.util.List;
import java.util.Set;

import com.kibootia.backend.access.RoleSummary;

/**
 * The signed-in user as {@code GET /api/v1/me} shows them. {@code emailVerified} is the token's word, not the stored
 * copy's, because the decision point enforces verification from the token (ADR 0041). {@code permissions} is for the
 * interface to decide what to show; the backend enforces every check itself, and a user who has not verified their
 * address holds none. {@code avatarsEnabled} says whether the files module is running, so the interface shows the avatar
 * controls only where they can work.
 */
public record MyProfile(UserProfile profile, boolean emailVerified, Set<String> permissions, List<RoleSummary> roles,
		boolean avatarsEnabled) {

}
