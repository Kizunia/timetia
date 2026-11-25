package com.kibootia.backend.timetable.domain;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;

/** Expands a weekly lecture into the dates it falls on. */
public final class WeeklyDates {

	private WeeklyDates() {
	}

	/**
	 * Every date from {@code from} to {@code to}, both included, that falls on the given day of the week
	 * (1 for Monday to 7 for Sunday), in order. Empty when the range is empty or contains no such day.
	 */
	public static List<LocalDate> of(int isoDayOfWeek, LocalDate from, LocalDate to) {
		List<LocalDate> dates = new ArrayList<>();
		if (to.isBefore(from)) {
			return dates;
		}
		DayOfWeek day = DayOfWeek.of(isoDayOfWeek);
		for (LocalDate date = from.with(TemporalAdjusters.nextOrSame(day)); !date.isAfter(to); date = date.plusWeeks(1)) {
			dates.add(date);
		}
		return dates;
	}

}
