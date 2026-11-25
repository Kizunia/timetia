/**
 * Date and time helpers for the timetable. A lecture's date and times are already in the campus's own time, as plain
 * strings (`2026-07-06`, `09:00:00`), so they are never turned into instants: every calculation goes through `Date.UTC`,
 * which does not depend on the zone of the machine, and nothing here uses `new Date("YYYY-MM-DD")`.
 */

/** A calendar date, `YYYY-MM-DD`. */
export type IsoDate = string;

const MS_PER_DAY = 86_400_000;

const pad = (n: number, width = 2) => String(n).padStart(width, "0");

export function parseIso(iso: IsoDate): { y: number; m: number; d: number } {
  const [y, m, d] = iso.split("-").map(Number);
  return { y: y!, m: m!, d: d! };
}

export function toIso(y: number, m: number, d: number): IsoDate {
  return `${pad(y, 4)}-${pad(m)}-${pad(d)}`;
}

const utc = (iso: IsoDate): number => {
  const { y, m, d } = parseIso(iso);
  return Date.UTC(y, m - 1, d);
};

const fromUtc = (ms: number): IsoDate => {
  const date = new Date(ms);
  return toIso(date.getUTCFullYear(), date.getUTCMonth() + 1, date.getUTCDate());
};

export function addDays(iso: IsoDate, n: number): IsoDate {
  return fromUtc(utc(iso) + n * MS_PER_DAY);
}

/** Monday is 1 and Sunday is 7. */
export function weekday(iso: IsoDate): 1 | 2 | 3 | 4 | 5 | 6 | 7 {
  const day = new Date(utc(iso)).getUTCDay();
  return (day === 0 ? 7 : day) as 1 | 2 | 3 | 4 | 5 | 6 | 7;
}

/** The Monday on or before the date. */
export function startOfWeek(iso: IsoDate): IsoDate {
  return addDays(iso, 1 - weekday(iso));
}

/** Monday to Saturday of the week that starts on `weekStart`. */
export function weekDays(weekStart: IsoDate): IsoDate[] {
  return Array.from({ length: 6 }, (_, i) => addDays(weekStart, i));
}

/** Today's date on the browser's own clock and zone. */
export function todayIso(now: Date = new Date()): IsoDate {
  return toIso(now.getFullYear(), now.getMonth() + 1, now.getDate());
}

function format(iso: IsoDate, options: Intl.DateTimeFormatOptions): string {
  return new Intl.DateTimeFormat("en-GB", { ...options, timeZone: "UTC" })
    .format(new Date(utc(iso)))
    .replace(",", "");
}

/** `Mon 6 Jul` */
export function formatDay(iso: IsoDate): string {
  return format(iso, { weekday: "short", day: "numeric", month: "short" });
}

/** `Monday 6 July 2026` */
export function formatDayLong(iso: IsoDate): string {
  return format(iso, { weekday: "long", day: "numeric", month: "long", year: "numeric" });
}

/** `6 Jul – 11 Jul 2026`, with the year of the last day shown. */
export function formatWeek(weekStart: IsoDate): string {
  const first = format(weekStart, { day: "numeric", month: "short" });
  const last = format(addDays(weekStart, 5), { day: "numeric", month: "short", year: "numeric" });
  return `${first} – ${last}`;
}

/** `09:00:00` becomes `09:00`. */
export function hhmm(time: string): string {
  return time.slice(0, 5);
}

/** `09:00:00` becomes 9. */
export function hourOf(time: string): number {
  return Number(time.slice(0, 2));
}

/** 9 becomes `09:00`, the form the API accepts. */
export function toApiTime(hour: number): string {
  return `${pad(hour)}:00`;
}

export function compareIso(a: IsoDate, b: IsoDate): number {
  return a < b ? -1 : a > b ? 1 : 0;
}
