package com.kibootia.backend.timetable.domain;

import java.time.LocalDate;
import java.util.UUID;

/** One thing held for one hour of one date: the unit that must be unique. {@code hour} is the hour of day, 8 to 19. */
public record ClaimKey(ClaimKind kind, UUID resourceId, LocalDate date, int hour) {

}
