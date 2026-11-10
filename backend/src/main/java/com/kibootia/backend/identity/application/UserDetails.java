package com.kibootia.backend.identity.application;

import java.util.List;

import com.kibootia.backend.access.RoleSummary;

/** A user as an administrator sees them: the profile and the roles the user holds. */
public record UserDetails(UserProfile profile, List<RoleSummary> roles) {

}
