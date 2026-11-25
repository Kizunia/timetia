package com.kibootia.backend.reference.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/departments}. The {@code code} is the stable short name and cannot be changed later. */
public record CreateDepartmentRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 120) String name) {

}
