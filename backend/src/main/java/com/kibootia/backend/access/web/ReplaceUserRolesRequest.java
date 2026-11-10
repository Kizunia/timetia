package com.kibootia.backend.access.web;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code PUT /api/v1/users/{id}/roles}: the roles the user should hold from now on. */
public record ReplaceUserRolesRequest(@NotNull @Size(max = 100) Set<@NotNull UUID> roleIds) {

}
