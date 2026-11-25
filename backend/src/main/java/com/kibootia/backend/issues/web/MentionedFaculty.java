package com.kibootia.backend.issues.web;

import java.util.UUID;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** A faculty member mentioned on an issue; {@code name} is null when the record was deleted. */
public record MentionedFaculty(@Schema(requiredMode = REQUIRED) UUID id, @Schema(nullable = true) String name) {

}
