package com.kibootia.backend.access.web;

import java.util.Set;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/roles}. The server validates; a client may check the same limits for usability. */
public record CreateRoleRequest(@NotBlank @Size(max = 80) String name, @NotNull @Size(max = 255) String description,
		@NotNull Set<@NotBlank @Size(max = 100) String> permissions) {

}
