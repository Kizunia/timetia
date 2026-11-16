import type { components } from "@/lib/api/schema.gen";

type Ticket = components["schemas"]["TicketResponse"];

export type Decision = "APPROVE" | "REJECT" | "ACKNOWLEDGE";

/** What the signed-in person is; only the id and the permissions matter here. */
export interface Viewer {
  id: string;
  permissions: readonly string[];
}

export interface AvailableActions {
  edit: boolean;
  apply: boolean;
  submit: boolean;
  withdraw: boolean;
  /** The answers this person may give to a review they were asked for; null when they have none to give. */
  review: { decisions: Decision[] } | null;
  escalate: boolean;
  revert: boolean;
  /** The one button to fill in, the rest are outlined. */
  primary: "apply" | "submit" | "review" | "edit" | null;
}

const REVERTIBLE: readonly Ticket["type"][] = ["RESCHEDULE", "CANCEL", "SUBSTITUTE", "EXCHANGE", "PERMANENT_CHANGE"];

/**
 * Which buttons a ticket page offers, worked out from the ticket as the detail endpoint returns it (domain.md section 3.4).
 * This is a hint for the screen: when the server disagrees it answers 403 or 409 and the screen shows why. Only the reviews
 * of the ticket's current revision count.
 */
export function availableActions(ticket: Ticket, me: Viewer): AvailableActions {
  const author = ticket.authorUserId === me.id;
  const current = ticket.reviews.filter((review) => review.revisionNo === ticket.revisionNo);
  const myPending = current.find((review) => review.reviewerUserId === me.id && review.status === "PENDING");
  const verdict = current.some((review) => review.status === "REJECTED")
    ? "REJECTED"
    : current.length === 0 || current.some((review) => review.status === "PENDING")
      ? "WAITING"
      : "APPROVED";
  const hodApproved = current.some((review) => review.capacity === "HOD" && review.status === "APPROVED");
  const onBehalf = ticket.actingFacultyId != null;
  const { type, status } = ticket;

  const edit = author && type !== "REVERT" && type !== "PERMANENT_CHANGE" && ["DRAFT", "IN_REVIEW", "REJECTED"].includes(status);
  const apply =
    author &&
    ((status === "DRAFT" && type !== "PERMANENT_CHANGE" && (type === "CANCEL" || type === "REVERT" || !onBehalf)) ||
      (status === "IN_REVIEW" && verdict === "APPROVED" && (type !== "PERMANENT_CHANGE" || hodApproved)));
  const submit = author && status === "DRAFT";
  const withdraw = author && (status === "DRAFT" || status === "IN_REVIEW");
  const review =
    status === "IN_REVIEW" && myPending
      ? {
          decisions: (type === "PERMANENT_CHANGE" && myPending.capacity === "HOD"
            ? ["APPROVE", "REJECT"]
            : ["APPROVE", "REJECT", "ACKNOWLEDGE"]) as Decision[],
        }
      : null;
  const escalate = author && status === "REJECTED";
  const revert =
    status === "APPLIED" &&
    ticket.revertedByTicketId == null &&
    REVERTIBLE.includes(type) &&
    (me.permissions.includes("rescheduling.tickets.revert-in-scope") ||
      (type !== "PERMANENT_CHANGE" && me.permissions.includes("rescheduling.tickets.revert")));

  let primary: AvailableActions["primary"] = null;
  if (status === "DRAFT") primary = apply ? "apply" : submit ? "submit" : null;
  else if (status === "IN_REVIEW") primary = review ? "review" : apply ? "apply" : null;
  else if (status === "REJECTED") primary = edit ? "edit" : null;

  return { edit, apply, submit, withdraw, review, escalate, revert, primary };
}
