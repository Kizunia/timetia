import { Badge } from "@/components/ui/badge";
import type { Lecture } from "@/features/timetable/api";
import { LectureCard } from "@/features/timetable/components/LectureCard";
import { groupByDay } from "@/features/timetable/lib/grid";
import { formatDay, todayIso, type IsoDate } from "@/lib/dates";

/**
 * A week as six day columns. On a narrow screen the columns stack, so the same markup serves a phone: a heading per day
 * and a list of that day's lectures under it.
 */
export function WeekGrid({
  weekStart,
  lectures,
  onSelect,
  today = todayIso(),
}: {
  weekStart: IsoDate;
  lectures: readonly Lecture[];
  onSelect: (id: string) => void;
  today?: IsoDate;
}) {
  return (
    <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
      {groupByDay(lectures, weekStart).map((day) => {
        const isToday = day.date === today;
        const headingId = `day-${day.date}`;
        return (
          <section key={day.date} aria-labelledby={headingId} className="flex min-w-0 flex-col gap-2">
            <h2
              id={headingId}
              aria-current={isToday ? "date" : undefined}
              className="flex items-center gap-2 text-sm font-semibold"
            >
              {formatDay(day.date)}
              {isToday && <Badge variant="secondary">Today</Badge>}
            </h2>
            {day.lectures.length === 0 ? (
              <p className="text-sm text-muted-foreground">No lectures</p>
            ) : (
              <ul className="flex flex-col gap-2">
                {day.lectures.map((lecture) => (
                  <li key={lecture.id}>
                    <LectureCard lecture={lecture} onSelect={onSelect} />
                  </li>
                ))}
              </ul>
            )}
          </section>
        );
      })}
    </div>
  );
}
