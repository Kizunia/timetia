package com.kibootia.backend.identity.application;

import java.util.List;
import java.util.Set;

import com.kibootia.backend.access.RoleSummary;

/**
 * The signed-in user as {@code GET /api/v1/me} shows them. {@code emailVerified} is the token's word, not the stored
 * copy's, because the decision point enforces verification from the token (ADR 0041). {@code permissions} is for the
 * interface to decide what to show; the backend enforces every check itself, and a user who has not verified their
 * address holds none. {@code avatarsEnabled} says whether the files module is running, so the interface shows the avatar
 * controls only where they can work. {@code filesEnabled} says the same thing for every other feature that keeps files
 * (a note's picture), so those features need not borrow the name of the avatar's flag. {@code administrator} says the
 * user holds the built-in administrator role (Timetia's Super Admin) with a verified address; the interface shows the
 * controls only an administrator can use, and the backend still refuses anyone else.
 */
public record MyProfile(UserProfile profile, boolean emailVerified, Set<String> permissions, List<RoleSummary> roles,
		boolean avatarsEnabled, boolean filesEnabled, boolean administrator) {

}
