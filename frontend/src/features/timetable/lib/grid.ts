import type { Lecture } from "@/features/timetable/api";
import { weekDays, type IsoDate } from "@/lib/dates";

export interface DayBucket {
  date: IsoDate;
  lectures: Lecture[];
}

const compare = (a: string | null | undefined, b: string | null | undefined) => (a ?? "").localeCompare(b ?? "");

/**
 * Six buckets, Monday to Saturday, each with that date's lectures in the order a day reads: start time, then division,
 * then batch. A lecture outside the six days is dropped; a cancelled one stays, because the timetable shows it marked.
 */
export function groupByDay(lectures: readonly Lecture[], weekStart: IsoDate): DayBucket[] {
  const buckets = weekDays(weekStart).map((date) => ({ date, lectures: [] as Lecture[] }));
  const byDate = new Map(buckets.map((bucket) => [bucket.date, bucket]));
  for (const lecture of lectures) byDate.get(lecture.date)?.lectures.push(lecture);
  for (const bucket of buckets) {
    bucket.lectures.sort(
      (a, b) =>
        compare(a.startTime, b.startTime) ||
        compare(a.divisionCode, b.divisionCode) ||
        compare(a.batchCode, b.batchCode) ||
        compare(a.id, b.id),
    );
  }
  return buckets;
}
