package com.kibootia.backend.access.application;

import java.util.Set;

/**
 * What an administrator supplies to create a role.
 *
 * @param scopeType the kind of place the role is assigned in, or null for a platform role
 */
public record NewRole(String name, String description, String scopeType, Set<String> permissions) {

}
