package com.kibootia.backend.reference;

import java.time.LocalDate;
import java.util.UUID;

/** An academic term; the base timetable's occurrences fall within {@code startDate} and {@code endDate}, inclusive. */
public record TermDetails(UUID id, String code, LocalDate startDate, LocalDate endDate) {

}
