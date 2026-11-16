import type { components } from "@/lib/api/schema.gen";
import { formatDay, hhmm } from "@/lib/dates";

type Schemas = components["schemas"];
type Ticket = Schemas["TicketResponse"];
type Change = Ticket["change"];

/** Anything that says when and where a lecture is: a target, where it was, or the lecture as it is now. */
interface Place {
  date: string;
  startTime: string;
  endTime: string;
  mode: "IN_PERSON" | "ONLINE";
  roomCode?: string | null;
}

export interface ChangeDescription {
  headline: string;
  from?: string;
  to?: string;
  /** Said in the person's own words, such as the reason for a cancellation. */
  detail?: string;
}

/** "Mon 6 Jul · 09:00–10:00 · R101", with "Online" in place of a room for an online lecture. */
export function slotText(place: Place): string {
  const where = place.roomCode ?? (place.mode === "ONLINE" ? "Online" : "—");
  return `${formatDay(place.date)} · ${hhmm(place.startTime)}–${hhmm(place.endTime)} · ${where}`;
}

/** What one change of a ticket does, as a headline and, where it moves something, from and to. */
export function describeChange(change: Change, ticket: Ticket): ChangeDescription {
  const subject = ticket.subjectCode ?? "lecture";
  switch (change.operation) {
    case "MOVE": {
      const was = change.before ?? change.lecture;
      return {
        headline: `Move ${subject}`,
        from: was ? slotText(was) : undefined,
        to: change.target ? slotText(change.target) : undefined,
      };
    }
    case "CANCEL":
      return {
        headline: change.lecture ? `Cancel ${subject} on ${formatDay(change.lecture.date)}` : `Cancel ${subject}`,
        detail: change.reason ?? undefined,
      };
    case "REASSIGN_FACULTY":
      return {
        headline: `New teacher for ${subject}`,
        from: change.beforeFacultyName ?? ticket.facultyName ?? undefined,
        to: change.substituteFacultyName ?? undefined,
        detail: change.reason ?? undefined,
      };
    case "CREATE_EXTRA":
      return { headline: `Add ${subject}`, to: change.target ? slotText(change.target) : undefined };
    case "RESTORE":
      return {
        headline: "Put back",
        from: change.lecture ? slotText(change.lecture) : undefined,
        to: change.target ? slotText(change.target) : undefined,
      };
  }
}
