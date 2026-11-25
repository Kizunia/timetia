package com.kibootia.backend.reference.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/subjects}. The {@code code} is fixed once set. */
public record CreateSubjectRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 150) String name) {

}
