package com.kibootia.backend.identity.web;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/users}: the address to invite and, optionally, roles to give besides the built-in user
 * role. Naming roles needs the permission to assign them.
 */
public record InviteUserRequest(@NotBlank @Size(max = 254) String email, @Size(max = 100) Set<@jakarta.validation.constraints.NotNull UUID> roleIds) {

}
