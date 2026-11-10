package com.kibootia.backend.access;

import java.util.UUID;

/**
 * A role as other modules see it: enough to show which roles a user holds.
 *
 * @param id the role's ID
 * @param name the role's name
 * @param builtIn whether the foundation created and protects the role (the administrator and user roles)
 */
public record RoleSummary(UUID id, String name, boolean builtIn) {

}
