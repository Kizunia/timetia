import { describe, expect, it } from "vitest";

import {
  addDays,
  compareIso,
  formatDay,
  formatDayLong,
  formatWeek,
  hhmm,
  hourOf,
  parseIso,
  startOfWeek,
  toApiTime,
  todayIso,
  toIso,
  weekDays,
  weekday,
} from "@/lib/dates";

// Run this file with TZ=America/Los_Angeles and with TZ=Asia/Kolkata: nothing below may depend on the machine's zone.
describe("dates", () => {
  it("parses and pads", () => {
    expect(parseIso("2026-07-06")).toEqual({ y: 2026, m: 7, d: 6 });
    expect(toIso(2026, 7, 6)).toBe("2026-07-06");
    expect(toIso(999, 1, 2)).toBe("0999-01-02");
  });

  it("adds days across months and years", () => {
    expect(addDays("2026-12-31", 1)).toBe("2027-01-01");
    expect(addDays("2026-07-06", 7)).toBe("2026-07-13");
    expect(addDays("2026-03-01", -1)).toBe("2026-02-28");
    expect(addDays("2024-02-28", 1)).toBe("2024-02-29");
  });

  it("numbers weekdays from Monday = 1 to Sunday = 7", () => {
    expect(weekday("2026-07-06")).toBe(1);
    expect(weekday("2026-07-11")).toBe(6);
    expect(weekday("2026-07-12")).toBe(7);
  });

  it("finds the Monday on or before a date", () => {
    expect(startOfWeek("2026-07-06")).toBe("2026-07-06");
    expect(startOfWeek("2026-07-12")).toBe("2026-07-06");
    expect(startOfWeek("2026-07-09")).toBe("2026-07-06");
    expect(startOfWeek("2027-01-01")).toBe("2026-12-28");
  });

  it("lists Monday to Saturday", () => {
    const days = weekDays("2026-07-06");
    expect(days).toHaveLength(6);
    expect(days[0]).toBe("2026-07-06");
    expect(days[5]).toBe("2026-07-11");
    expect(weekday(days[5]!)).toBe(6);
  });

  it("reads today from the local calendar", () => {
    expect(todayIso(new Date(2026, 6, 6, 23, 59))).toBe("2026-07-06");
    expect(todayIso(new Date(2026, 6, 7, 0, 1))).toBe("2026-07-07");
  });

  it("formats days and weeks", () => {
    expect(formatDay("2026-07-06")).toBe("Mon 6 Jul");
    expect(formatDayLong("2026-07-06")).toBe("Monday 6 July 2026");
    expect(formatWeek("2026-07-06")).toBe("6 Jul – 11 Jul 2026");
    expect(formatWeek("2026-12-28")).toBe("28 Dec – 2 Jan 2027");
  });

  it("handles times", () => {
    expect(hhmm("09:00:00")).toBe("09:00");
    expect(hhmm("09:00")).toBe("09:00");
    expect(hourOf("14:00:00")).toBe(14);
    expect(toApiTime(9)).toBe("09:00");
    expect(toApiTime(14)).toBe("14:00");
  });

  it("compares", () => {
    expect(compareIso("2026-07-06", "2026-07-07")).toBe(-1);
    expect(compareIso("2026-07-07", "2026-07-06")).toBe(1);
    expect(compareIso("2026-07-06", "2026-07-06")).toBe(0);
  });
});
