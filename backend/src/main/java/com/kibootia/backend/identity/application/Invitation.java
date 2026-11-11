package com.kibootia.backend.identity.application;

import java.util.Set;
import java.util.UUID;

/**
 * What an administrator supplies to invite someone: an email address and, optionally, roles to give them besides the
 * built-in user role. Naming roles needs the permission to assign roles.
 */
public record Invitation(String email, Set<UUID> roleIds) {

}
