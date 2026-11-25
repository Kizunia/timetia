"use client";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import type { Ticket } from "@/features/tickets/api";
import { availableActions, type Viewer } from "@/features/tickets/lib/actions";

export interface TicketHandlers {
  onEdit: () => void;
  onApply: () => void;
  onSubmit: () => void;
  onWithdraw: () => void;
  onReview: () => void;
  onEscalate: () => void;
  onRevert: () => void;
}

/**
 * Exactly the buttons the person may use on this ticket, the main one filled and the rest outlined. Which buttons is worked
 * out from the ticket (see `availableActions`), and the server has the last word. Apply stays visible but disabled while a
 * conflict stands, so the person sees why.
 */
export function ActionBar({
  ticket,
  me,
  busy,
  handlers,
}: {
  ticket: Ticket;
  me: Viewer;
  busy: boolean;
  handlers: TicketHandlers;
}) {
  const actions = availableActions(ticket, me);
  const variant = (name: NonNullable<typeof actions.primary>) => (actions.primary === name ? "default" : "outline");
  const anything =
    actions.edit || actions.apply || actions.submit || actions.withdraw || actions.review || actions.escalate || actions.revert;
  if (!anything) return <p className="text-sm text-muted-foreground">There is nothing for you to do on this ticket.</p>;
  return (
    <div className="flex flex-wrap items-center gap-2">
      {actions.review && (
        <Button variant={variant("review")} disabled={busy} onClick={handlers.onReview}>
          Review
        </Button>
      )}
      {actions.apply && (
        <Button
          variant={variant("apply")}
          disabled={busy || ticket.conflicted}
          title={ticket.conflicted ? "Resolve the conflicts first" : undefined}
          onClick={handlers.onApply}
        >
          Apply
        </Button>
      )}
      {actions.submit && (
        <Button variant={variant("submit")} disabled={busy} onClick={handlers.onSubmit}>
          Send for review
        </Button>
      )}
      {actions.edit && (
        <Button variant={variant("edit")} disabled={busy} onClick={handlers.onEdit}>
          Edit
        </Button>
      )}
      {actions.escalate && (
        <Button variant="outline" disabled={busy} onClick={handlers.onEscalate}>
          Take to the HOD
        </Button>
      )}
      {actions.revert && (
        <Button variant="outline" disabled={busy} onClick={handlers.onRevert}>
          Revert
        </Button>
      )}
      {actions.withdraw && (
        <AlertDialog>
          <AlertDialogTrigger asChild>
            <Button variant="outline" disabled={busy}>
              Withdraw
            </Button>
          </AlertDialogTrigger>
          <AlertDialogContent>
            <AlertDialogHeader>
              <AlertDialogTitle>Withdraw this ticket?</AlertDialogTitle>
              <AlertDialogDescription>
                Nothing changes in the timetable. A withdrawn ticket cannot be brought back; raise a new one instead.
              </AlertDialogDescription>
            </AlertDialogHeader>
            <AlertDialogFooter>
              <AlertDialogCancel>Keep it</AlertDialogCancel>
              <AlertDialogAction onClick={handlers.onWithdraw}>Withdraw</AlertDialogAction>
            </AlertDialogFooter>
          </AlertDialogContent>
        </AlertDialog>
      )}
    </div>
  );
}
