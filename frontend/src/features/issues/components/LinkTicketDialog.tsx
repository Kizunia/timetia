"use client";

import { toast } from "sonner";

import { useLinkTicket, type Issue } from "@/features/issues/api";
import { PickDialog } from "@/features/issues/components/PickDialog";
import { useTickets, type Ticket } from "@/features/tickets/api";
import { TICKET_STATUS_LABEL, TICKET_TYPE_LABEL } from "@/lib/labels";

function describe(ticket: Ticket): string {
  const raised = new Date(ticket.createdAt).toLocaleDateString();
  return `${TICKET_TYPE_LABEL[ticket.type]} · ${TICKET_STATUS_LABEL[ticket.status]} · ${raised} · ${ticket.id.slice(-6)}`;
}

/** Links one of the person's own tickets as the answer to the issue. */
export function LinkTicketDialog({ issue, onClose }: { issue: Issue; onClose: () => void }) {
  const tickets = useTickets({ page: 0, size: 100, mine: true, sort: "createdAt,desc" });
  const link = useLinkTicket(issue.id);
  const options = (tickets.data?.items ?? [])
    .filter((ticket) => !issue.linkedTicketIds.includes(ticket.id))
    .map((ticket) => ({ value: ticket.id, label: describe(ticket) }));

  return (
    <PickDialog
      title="Link a ticket"
      description="The ticket that answers this issue. Raise it first, from a lecture on the timetable."
      label="Ticket"
      empty={tickets.isPending ? "Loading your tickets…" : "You have no ticket to link yet."}
      options={options}
      confirm="Link"
      pending={link.isPending}
      error={link.error ?? tickets.error}
      onConfirm={async (ticketId) => {
        await link.mutateAsync(ticketId);
        toast.success("Ticket linked");
      }}
      onClose={onClose}
    />
  );
}
