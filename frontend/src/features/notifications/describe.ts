import type { Notification } from "@/features/notifications/api";

const TEXT: Record<Notification["type"], string> = {
  REVIEW_REQUESTED: "You were asked to review a ticket",
  REVIEW_APPROVED: "A reviewer approved a ticket",
  REVIEW_REJECTED: "A reviewer rejected a ticket",
  TICKET_APPLIED: "A ticket that affects you was applied",
  TICKET_CONFLICTED: "A ticket now has a conflict",
  TICKET_EDITED: "A ticket you are involved in was edited",
  TICKET_WITHDRAWN: "A ticket was withdrawn",
  TICKET_REVERTED: "A ticket was reverted",
  ISSUE_MENTION: "You were mentioned in an issue",
};

/** What a notification says and where it leads: the ticket or the issue it is about. */
export function describeNotification(n: Pick<Notification, "type" | "ticketId" | "issueId">): { text: string; href: string | null } {
  return {
    text: TEXT[n.type],
    href: n.ticketId ? `/tickets/${n.ticketId}` : n.issueId ? `/issues/${n.issueId}` : null,
  };
}
