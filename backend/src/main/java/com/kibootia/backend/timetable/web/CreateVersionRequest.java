package com.kibootia.backend.timetable.web;

import java.time.LocalDate;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;

/**
 * The body of {@code POST /api/v1/timetable/versions}: a new draft for a division in a term. {@code effectiveFrom} is the
 * W.E.F. date from which it applies; it must fall within the term.
 */
public record CreateVersionRequest(@NotNull UUID divisionId, @NotNull UUID termId, @NotNull LocalDate effectiveFrom) {

}
