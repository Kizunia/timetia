package com.kibootia.backend.timetable.web;

import io.swagger.v3.oas.annotations.media.Schema;

import static io.swagger.v3.oas.annotations.media.Schema.RequiredMode.REQUIRED;

/** The result of publishing a version: the version, and how many dated lectures it created and replaced. */
public record PublishedVersionResponse(
		@Schema(requiredMode = REQUIRED) TimetableVersionResponse version,
		@Schema(requiredMode = REQUIRED) int occurrencesCreated,
		@Schema(requiredMode = REQUIRED) int occurrencesSuperseded) {

}
