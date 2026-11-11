package com.kibootia.backend.access.web;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * The body of {@code POST /api/v1/roles}. The server validates; a client may check the same limits for usability.
 * {@code scopeType} is left out for a platform role. A role that holds policy-decided permissions needs one, and a role
 * with one holds only those (Timetia ADR 0004).
 */
public record CreateRoleRequest(@NotBlank @Size(max = 80) String name, @NotNull @Size(max = 255) String description,
		@NotNull Set<@NotBlank @Size(max = 100) String> permissions, @Size(max = 20) String scopeType) {

}
