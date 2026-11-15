package com.kibootia.backend.reference.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

import com.kibootia.backend.academic.SessionType;

/** The body of {@code POST /api/v1/teaching-assignments}. {@code batchId} is null for theory, required for lab/tutorial. */
public record CreateTeachingAssignmentRequest(@NotNull UUID divisionId, UUID batchId, @NotNull UUID subjectId,
		@NotNull UUID facultyId, @NotNull SessionType sessionType) {

}
