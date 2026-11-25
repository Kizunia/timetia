import type { components } from "@/lib/api/schema.gen";
import { TICKET_TYPE_LABEL } from "@/lib/labels";

type Notification = components["schemas"]["NotificationResponse"];

export type Described = Pick<
  Notification,
  "type" | "ticketId" | "issueId" | "lectureId" | "actorName" | "ticketType" | "ticketVisible"
>;

/** The sentences of domain.md section 5. */
const TEXT: Record<Notification["type"], (actor: string, kind: string) => string> = {
  REVIEW_REQUESTED: (actor, kind) => `${actor} asked you to review a ${kind}`,
  REVIEW_APPROVED: (actor, kind) => `${actor} approved your ${kind}`,
  REVIEW_REJECTED: (actor, kind) => `${actor} rejected your ${kind}`,
  TICKET_APPLIED: (_, kind) => `A ${kind} was applied`,
  TICKET_REVERTED: (_, kind) => `A ${kind} was undone`,
  TICKET_CONFLICTED: (_, kind) => `Something is now in the way of a ${kind}`,
  TICKET_EDITED: (actor, kind) => `${actor} changed a ${kind} you were asked to review. Please look again`,
  TICKET_WITHDRAWN: (actor, kind) => `${actor} withdrew a ${kind} you were asked to review`,
  ISSUE_MENTION: (actor) => `${actor} mentioned you on an issue`,
};

function kindOf(ticketType: string | null | undefined): string {
  const label = (TICKET_TYPE_LABEL as Record<string, string | undefined>)[ticketType ?? ""];
  return label ? label.toLowerCase() : "ticket";
}

/**
 * What a notification says and where it leads. A person who may not open the ticket (a student, or a representative of the
 * other kind of lecture) is sent to the lecture on the timetable instead, and gets no link when there is no lecture.
 */
export function describeNotification(n: Described): { text: string; href: string | null } {
  const text = TEXT[n.type](n.actorName ?? "Someone", kindOf(n.ticketType));
  if (n.issueId) return { text, href: `/issues/${n.issueId}` };
  if (n.ticketId && n.ticketVisible) return { text, href: `/tickets/${n.ticketId}` };
  return { text, href: n.lectureId ? `/timetable?lecture=${n.lectureId}` : null };
}
