import type { Lecture } from "@/features/timetable/api";

export interface Flag {
  label: string;
  variant: "destructive" | "outline" | "secondary";
}

/** The subject's code, or a word for a subject that was deleted. */
export function lectureTitle(lecture: Lecture): string {
  return lecture.subjectCode ?? "Deleted subject";
}

/** The room's code, `Online`, or a dash when there is neither. */
export function lectureWhere(lecture: Lecture): string {
  return lecture.mode === "ONLINE" ? "Online" : (lecture.roomCode ?? "—");
}

/** `Theory · CSE-A` for the whole division, `Lab · CSE-A1` for one batch. */
export function lectureAudience(lecture: Lecture): string {
  const session = lecture.sessionType === "THEORY" ? "Theory" : lecture.sessionType === "LAB" ? "Lab" : "Tutorial";
  return `${session} · ${lecture.batchCode ?? lecture.divisionCode ?? "—"}`;
}

/** The badges of a lecture, in the order they are shown. Each is words as well as a variant, never colour alone. */
export function lectureFlags(lecture: Lecture): Flag[] {
  const flags: Flag[] = [];
  const moved = Boolean(lecture.originalDate);
  const substituted = Boolean(lecture.originalFacultyId);
  if (lecture.cancelled) flags.push({ label: "Cancelled", variant: "destructive" });
  if (lecture.extra) flags.push({ label: "Extra", variant: "outline" });
  if (moved) flags.push({ label: "Moved", variant: "secondary" });
  if (lecture.changed && !moved && !lecture.cancelled && !lecture.extra && !substituted) {
    flags.push({ label: "Changed", variant: "secondary" });
  }
  if (substituted) flags.push({ label: "Substitute", variant: "outline" });
  if (lecture.mode === "ONLINE") flags.push({ label: "Online", variant: "outline" });
  return flags;
}
