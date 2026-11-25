import type { Lecture } from "@/features/timetable/api";
import { formatDay, hhmm } from "@/lib/dates";

/**
 * The lectures this one can be exchanged with. The server decides, but it would refuse most of the others, so the screen
 * offers only these: the same kind of session for the same batch, taught by someone else, not cancelled, not this lecture and
 * not yet started.
 */
export function exchangeCandidates(lectures: readonly Lecture[], lecture: Lecture, now: Date = new Date()): Lecture[] {
  return lectures.filter(
    (other) =>
      other.id !== lecture.id &&
      other.sessionType === lecture.sessionType &&
      (other.batchId ?? null) === (lecture.batchId ?? null) &&
      other.facultyId !== lecture.facultyId &&
      !other.cancelled &&
      new Date(`${other.date}T${other.startTime}`) > now,
  );
}

/** "Mon 13 Jul 09:00–10:00 · OOP · Dr Roy" */
export function exchangeLabel(other: Lecture): string {
  return `${formatDay(other.date)} ${hhmm(other.startTime)}–${hhmm(other.endTime)} · ${other.subjectCode ?? "—"} · ${other.facultyName ?? "—"}`;
}
