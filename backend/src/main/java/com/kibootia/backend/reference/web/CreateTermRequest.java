package com.kibootia.backend.reference.web;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** The body of {@code POST /api/v1/academic-terms}. The end date must not be before the start date. */
public record CreateTermRequest(@NotBlank @Size(max = 30) String code, @NotBlank @Size(max = 120) String name,
		@NotNull LocalDate startDate, @NotNull LocalDate endDate) {

}
