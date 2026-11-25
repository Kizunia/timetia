import type { Ticket, TicketChange } from "@/features/tickets/api";
import { describeChange } from "@/features/tickets/lib/describe";

/** More than this many changes (a permanent change moves a whole series) are folded after the first few. */
const SHOWN = 3;
const FOLD_ABOVE = 5;

function Change({ change, ticket }: { change: TicketChange; ticket: Ticket }) {
  const described = describeChange(change, ticket);
  return (
    <li className="rounded-lg border p-3">
      <p className="font-medium">{described.headline}</p>
      {(described.from || described.to) && (
        <div className="mt-1 flex flex-col gap-1 text-sm sm:flex-row sm:items-center sm:gap-3">
          {described.from && <span className="text-muted-foreground">{described.from}</span>}
          {described.from && described.to && (
            <span aria-hidden="true" className="hidden sm:inline">
              →
            </span>
          )}
          {described.from && described.to && <span className="sr-only">changes to</span>}
          {described.to && <span>{described.to}</span>}
        </div>
      )}
      {described.detail && <p className="mt-1 text-sm text-muted-foreground">{described.detail}</p>}
    </li>
  );
}

/** What the ticket does to each lecture: what it was, and what it becomes. */
export function ChangeSummary({ ticket }: { ticket: Ticket }) {
  const changes = ticket.changes;
  const folded = changes.length > FOLD_ABOVE;
  const shown = folded ? changes.slice(0, SHOWN) : changes;
  const rest = folded ? changes.slice(SHOWN) : [];
  return (
    <div className="flex flex-col gap-2">
      <ul className="flex flex-col gap-2">
        {shown.map((change, index) => (
          <Change key={index} change={change} ticket={ticket} />
        ))}
      </ul>
      {rest.length > 0 && (
        <details>
          <summary className="cursor-pointer text-sm font-medium text-primary">and {rest.length} more lectures</summary>
          <ul className="mt-2 flex flex-col gap-2">
            {rest.map((change, index) => (
              <Change key={index} change={change} ticket={ticket} />
            ))}
          </ul>
        </details>
      )}
    </div>
  );
}
