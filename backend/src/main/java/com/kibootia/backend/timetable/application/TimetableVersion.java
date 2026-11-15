package com.kibootia.backend.timetable.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A version of one division's base timetable (ADR 0004): the weekly pattern that applies from {@code effectiveFrom}
 * (W.E.F.) until a later published version takes over, within one academic term.
 */
public record TimetableVersion(UUID id, UUID divisionId, UUID termId, LocalDate effectiveFrom, VersionStatus status,
		long version, Instant createdAt, Instant updatedAt) {

}
