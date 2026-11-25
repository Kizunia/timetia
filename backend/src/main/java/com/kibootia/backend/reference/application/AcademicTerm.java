package com.kibootia.backend.reference.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/** An academic term (a semester) as the use cases see it (ADR 0004). Its dates bound the base timetable's occurrences. */
public record AcademicTerm(UUID id, String code, String name, LocalDate startDate, LocalDate endDate, long version,
		Instant createdAt, Instant updatedAt) {

}
