import { Badge } from "@/components/ui/badge";
import type { Lecture } from "@/features/timetable/api";
import { lectureAudience, lectureFlags, lectureTitle, lectureWhere } from "@/features/timetable/lib/describe";
import { formatDay, hhmm } from "@/lib/dates";
import { cn } from "@/lib/utils";

/** One lecture in a day's list. It is a button, so the keyboard opens it, and its label says everything in one sentence. */
export function LectureCard({ lecture, onSelect }: { lecture: Lecture; onSelect: (id: string) => void }) {
  const flags = lectureFlags(lecture);
  const was =
    lecture.originalDate && lecture.originalStartTime
      ? `was ${formatDay(lecture.originalDate)} ${hhmm(lecture.originalStartTime)}`
      : null;
  const teacher = lecture.facultyName ?? "—";
  const time = `${hhmm(lecture.startTime)}–${hhmm(lecture.endTime)}`;
  const label = [
    `${lectureTitle(lecture)}, ${lectureAudience(lecture)}`,
    time,
    `with ${teacher}`,
    `in ${lectureWhere(lecture)}`,
    ...flags.map((flag) => flag.label),
    ...(was ? [was] : []),
  ].join(", ");

  return (
    <button
      type="button"
      aria-label={label}
      onClick={() => onSelect(lecture.id)}
      className={cn(
        "flex w-full flex-col gap-1 rounded-lg border bg-card p-3 text-left hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring",
        lecture.cancelled && "text-muted-foreground",
      )}
    >
      <span className="text-xs text-muted-foreground">{time}</span>
      <span className={cn("font-semibold", lecture.cancelled && "line-through")}>{lectureTitle(lecture)}</span>
      <span className="text-sm">{lectureAudience(lecture)}</span>
      <span className="text-sm">{teacher}</span>
      <span className="text-sm">{lectureWhere(lecture)}</span>
      {flags.length > 0 && (
        <span className="mt-1 flex flex-wrap gap-1">
          {flags.map((flag) => (
            <Badge key={flag.label} variant={flag.variant}>
              {flag.label}
            </Badge>
          ))}
        </span>
      )}
      {was && <span className="text-xs text-muted-foreground">{was}</span>}
    </button>
  );
}
