import type { components } from "@/lib/api/schema.gen";

import { toTarget, type SlotValues } from "./slot";

type Schemas = components["schemas"];
export type CreateTicketRequest = Schemas["CreateTicketRequest"];
export type UpdateTicketRequest = Schemas["UpdateTicketRequest"];

/** The kinds of ticket a person raises. A revert is made from an applied ticket and never raised on its own. */
export type TicketKind = "RESCHEDULE" | "CANCEL" | "SUBSTITUTE" | "EXCHANGE" | "PERMANENT_CHANGE" | "EXTRA";

/** Everything any of the ticket forms holds; each kind reads only its own fields. */
export interface TicketFormValues extends SlotValues {
  description?: string;
  reason?: string;
  substituteFacultyId?: string;
  otherLectureId?: string;
  assignmentId?: string;
}

/** A trimmed text, or undefined when there is none: a field that is empty must be absent, not empty, in the request. */
function given(value: string | undefined): string | undefined {
  const trimmed = value?.trim();
  return trimmed ? trimmed : undefined;
}

/**
 * The body that raises a ticket. A field a kind does not take is not sent: the server refuses it with `not_allowed`
 * (domain.md section 3.1).
 */
export function buildCreateBody(kind: TicketKind, lectureId: string | null, values: TicketFormValues): CreateTicketRequest {
  const description = given(values.description);
  const reason = given(values.reason);
  switch (kind) {
    case "RESCHEDULE":
    case "PERMANENT_CHANGE":
      return { type: kind, lectureId: lectureId!, description, reason, target: toTarget(values) };
    case "CANCEL":
      return { type: kind, lectureId: lectureId!, reason, description };
    case "SUBSTITUTE":
      return { type: kind, lectureId: lectureId!, substituteFacultyId: values.substituteFacultyId, reason, description };
    case "EXCHANGE":
      return { type: kind, lectureId: lectureId!, otherLectureId: values.otherLectureId, description };
    case "EXTRA":
      return { type: kind, assignmentId: values.assignmentId, description, target: toTarget(values) };
  }
}

/**
 * The body that replaces the plan of a ticket that is not applied yet. The whole plan is sent again, with the version that
 * was read, so a ticket changed meanwhile is refused rather than overwritten.
 */
export function buildEditBody(type: Schemas["TicketResponse"]["type"], version: number, values: TicketFormValues): UpdateTicketRequest {
  const description = given(values.description);
  const reason = given(values.reason);
  switch (type) {
    case "RESCHEDULE":
      return { version, description, reason, target: toTarget(values) };
    case "CANCEL":
      return { version, reason, description };
    case "SUBSTITUTE":
      return { version, substituteFacultyId: values.substituteFacultyId, reason, description };
    case "EXTRA":
      return { version, description, target: toTarget(values) };
    default:
      // An exchange changes only what it says; a permanent change or a revert cannot be edited at all.
      return { version, description };
  }
}
