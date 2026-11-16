import { Badge } from "@/components/ui/badge";
import type { Ticket } from "@/features/tickets/api";
import { TICKET_STATUS_LABEL, TICKET_STATUS_VARIANT } from "@/lib/labels";

/** The state of a ticket. A draft or a ticket under review with something in its way also says so, in words and colour. */
export function StatusBadge({ ticket }: { ticket: Pick<Ticket, "status" | "conflicted"> }) {
  const open = ticket.status === "DRAFT" || ticket.status === "IN_REVIEW";
  return (
    <span className="inline-flex flex-wrap items-center gap-1">
      <Badge variant={TICKET_STATUS_VARIANT[ticket.status]}>{TICKET_STATUS_LABEL[ticket.status]}</Badge>
      {ticket.conflicted && open && <Badge variant="destructive">Conflict</Badge>}
    </span>
  );
}
