package com.kibootia.backend.issues.web;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.kibootia.backend.academic.SessionType;

/**
 * The body of {@code POST /api/v1/issues}. A {@code THEORY} issue names the division ({@code divisionId}); a {@code LAB} or
 * {@code TUTORIAL} issue names the batch ({@code batchId}). {@code mentionedFacultyIds} are the faculty it concerns, by
 * faculty record; each is told.
 */
public record CreateIssueRequest(@NotNull SessionType sessionType, UUID divisionId, UUID batchId,
		@NotBlank @Size(max = 200) String title, @Size(max = 2000) String description,
		@Size(max = 10) Set<UUID> mentionedFacultyIds) {

}
