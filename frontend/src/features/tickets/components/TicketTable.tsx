import Link from "next/link";

import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Ticket } from "@/features/tickets/api";
import { StatusBadge } from "@/features/tickets/components/StatusBadge";
import { formatDay } from "@/lib/dates";
import { TICKET_TYPE_LABEL } from "@/lib/labels";

/** "OOP · CSE-A1 · Mon 6 Jul": what the ticket is about. A list has no lecture, so the date is the target's or where it was. */
function lectureText(ticket: Ticket): string {
  const date = ticket.change.target?.date ?? ticket.change.before?.date;
  return [ticket.subjectCode ?? "—", ticket.batchCode ?? ticket.divisionCode ?? "—", date ? formatDay(date) : null]
    .filter(Boolean)
    .join(" · ");
}

/** A page of tickets, each linked to its own page from the first cell. */
export function TicketTable({ tickets, empty }: { tickets: readonly Ticket[]; empty: string }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Type</TableHead>
          <TableHead>Lecture</TableHead>
          <TableHead>People</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Updated</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {tickets.map((ticket) => (
          <TableRow key={ticket.id} data-testid="ticket-row">
            <TableCell>
              <Link href={`/tickets/${ticket.id}`} className="font-medium text-primary underline underline-offset-2">
                {TICKET_TYPE_LABEL[ticket.type]}
              </Link>
            </TableCell>
            <TableCell>{lectureText(ticket)}</TableCell>
            <TableCell>by {ticket.authorName ?? "—"}</TableCell>
            <TableCell>
              <StatusBadge ticket={ticket} />
            </TableCell>
            <TableCell>{new Date(ticket.updatedAt).toLocaleDateString()}</TableCell>
          </TableRow>
        ))}
        {tickets.length === 0 && (
          <TableRow>
            <TableCell colSpan={5} className="text-center text-muted-foreground">
              {empty}
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  );
}
