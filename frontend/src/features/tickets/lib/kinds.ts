import type { Lecture } from "@/features/timetable/api";

import type { TicketKind } from "./request";

type Raisable = Exclude<TicketKind, "EXTRA">;

/**
 * The kinds of ticket to offer on a lecture. The server decides who may do what; this is only so that a person is not offered
 * what they obviously cannot do. A lecture that is cancelled or has started cannot be changed. The teacher of a lecture may
 * change it, and so may an HOD on their behalf (a different permission). A permanent change is for a weekly lecture, not an
 * extra one.
 */
export function raisableKinds(
  lecture: Lecture,
  permissions: readonly string[],
  teachesIt: boolean,
  now: Date = new Date(),
): Raisable[] {
  if (lecture.cancelled) return [];
  if (new Date(`${lecture.date}T${lecture.startTime}`) <= now) return [];
  const has = (permission: string) => permissions.includes(`rescheduling.tickets.${permission}`);
  const mayChange = has("create-on-behalf") || (has("create") && teachesIt);
  const mayCancel = has("cancel-on-behalf") || (has("cancel") && teachesIt);
  const kinds: Raisable[] = [];
  if (mayChange) kinds.push("RESCHEDULE");
  if (mayCancel) kinds.push("CANCEL");
  if (mayChange) kinds.push("SUBSTITUTE", "EXCHANGE");
  if (mayChange && !lecture.extra) kinds.push("PERMANENT_CHANGE");
  return kinds;
}
