package com.kibootia.backend.timetable.domain;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class WeeklyDatesTest {

	/** 2026-07-06 is a Monday. */
	static final LocalDate MONDAY = LocalDate.of(2026, 7, 6);

	@Test
	@DisplayName("a weekly lecture falls on that weekday of every week in the range, both ends included")
	void everyWeek() {
		List<LocalDate> dates = WeeklyDates.of(3, MONDAY, MONDAY.plusDays(21));

		assertThat(dates).containsExactly(LocalDate.of(2026, 7, 8), LocalDate.of(2026, 7, 15), LocalDate.of(2026, 7, 22));
	}

	@Test
	@DisplayName("the first and last days of the range are included when they fall on the weekday")
	void rangeEndsAreIncluded() {
		assertThat(WeeklyDates.of(1, MONDAY, MONDAY.plusWeeks(2))).containsExactly(MONDAY, MONDAY.plusWeeks(1),
				MONDAY.plusWeeks(2));
	}

	@Test
	@DisplayName("a range that starts after the weekday begins at the next one")
	void startsAtTheNextOccurrence() {
		assertThat(WeeklyDates.of(1, MONDAY.plusDays(1), MONDAY.plusWeeks(2))).containsExactly(MONDAY.plusWeeks(1),
				MONDAY.plusWeeks(2));
	}

	@Test
	@DisplayName("an empty or too short range has no dates")
	void emptyRanges() {
		assertThat(WeeklyDates.of(1, MONDAY, MONDAY.minusDays(1))).isEmpty();
		assertThat(WeeklyDates.of(5, MONDAY, MONDAY.plusDays(2))).isEmpty();
	}

}
