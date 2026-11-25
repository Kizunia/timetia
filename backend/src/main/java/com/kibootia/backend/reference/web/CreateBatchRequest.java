package com.kibootia.backend.reference.web;

import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/batches}: a batch under an existing division. */
public record CreateBatchRequest(@NotBlank @Size(max = 40) String code, @NotBlank @Size(max = 120) String name,
		@NotNull UUID divisionId) {

}
