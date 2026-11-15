package com.kibootia.backend.reference.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/divisions}: a division under an existing department. */
public record CreateDivisionRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String name,
		@NotNull UUID departmentId) {

}
