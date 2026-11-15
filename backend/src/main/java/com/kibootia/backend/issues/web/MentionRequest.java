package com.kibootia.backend.issues.web;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/** The body of {@code POST /api/v1/issues/{id}/mentions}: the faculty member to mention. */
public record MentionRequest(@NotNull UUID facultyId) {

}
