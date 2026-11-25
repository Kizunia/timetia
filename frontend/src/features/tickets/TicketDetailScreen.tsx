"use client";

import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Skeleton } from "@/components/ui/skeleton";
import { usePermissions } from "@/features/identity/permissions";
import { useApplyTicket, useTicket, useWithdrawTicket } from "@/features/tickets/api";
import { ActionBar } from "@/features/tickets/components/ActionBar";
import { ChangeSummary } from "@/features/tickets/components/ChangeSummary";
import { ConflictList } from "@/features/tickets/components/ConflictList";
import { EditTicketDialog } from "@/features/tickets/components/EditTicketDialog";
import { EscalateDialog } from "@/features/tickets/components/EscalateDialog";
import { ReviewDialog } from "@/features/tickets/components/ReviewDialog";
import { ReviewList } from "@/features/tickets/components/ReviewList";
import { RevertDialog } from "@/features/tickets/components/RevertDialog";
import { StatusBadge } from "@/features/tickets/components/StatusBadge";
import { SubmitDialog } from "@/features/tickets/components/SubmitDialog";
import { availableActions } from "@/features/tickets/lib/actions";
import { BLOCKED, conflictsOf } from "@/features/tickets/lib/problems";
import { ApiError } from "@/lib/api/problem";
import { TICKET_TYPE_LABEL } from "@/lib/labels";

type Dialog = "edit" | "submit" | "review" | "escalate" | "revert" | null;

/** One ticket: what it does, what stands in the way, who was asked, and exactly the actions the person may take. */
export function TicketDetailScreen({ id }: { id: string }) {
  const { me } = usePermissions();
  const query = useTicket(id);
  const apply = useApplyTicket(id);
  const withdraw = useWithdrawTicket(id);
  const [dialog, setDialog] = useState<Dialog>(null);
  const [actionError, setActionError] = useState<unknown>(null);

  const ticket = query.data;

  const run = async (action: () => Promise<unknown>, done: string) => {
    setActionError(null);
    try {
      await action();
      toast.success(done);
    } catch (error) {
      setActionError(error);
    }
  };

  if (query.isError) {
    return (
      <section aria-labelledby="ticket-heading" className="grid gap-4">
        <h1 id="ticket-heading" className="sr-only">
          Ticket
        </h1>
        <BackLink />
        <ErrorAlert error={query.error} title="The ticket could not be opened" />
      </section>
    );
  }
  if (!ticket) {
    return (
      <section aria-labelledby="ticket-heading" aria-busy="true" className="grid gap-4">
        <h1 id="ticket-heading" className="sr-only">
          Ticket
        </h1>
        <BackLink />
        <Skeleton className="h-8 w-2/3" />
        <Skeleton className="h-32 w-full" />
      </section>
    );
  }

  const actions = availableActions(ticket, me);
  const blocked = actionError instanceof ApiError && actionError.code === BLOCKED;
  // What stands in the way: the stored result of the latest check, or, until it has been read again, what the refusal said.
  const conflicts = ticket.conflicts.length > 0 ? ticket.conflicts : conflictsOf(actionError);
  const title = [
    TICKET_TYPE_LABEL[ticket.type],
    ticket.subjectName ?? "Lecture",
    [ticket.divisionCode, ticket.batchCode].filter(Boolean).join(" · "),
  ]
    .filter(Boolean)
    .join(" · ");

  return (
    <section aria-labelledby="ticket-heading" className="grid gap-6">
      <BackLink />
      <div className="flex flex-wrap items-center gap-3">
        <h1 id="ticket-heading" className="text-2xl font-semibold tracking-tight">
          {title}
        </h1>
        <StatusBadge ticket={ticket} />
      </div>
      <p className="text-sm text-muted-foreground">
        Raised by {ticket.authorName ?? "—"}
        {ticket.actingFacultyId && <> for {ticket.facultyName ?? "—"}</>} on {new Date(ticket.createdAt).toLocaleString()}. Expires{" "}
        {new Date(ticket.expiresAt).toLocaleString()} (when the earliest lecture starts).
        {ticket.revisionNo > 1 && <> Round {ticket.revisionNo}.</>}
      </p>
      {ticket.description && <p>{ticket.description}</p>}

      <ChangeSummary ticket={ticket} />
      {(ticket.revertsTicketId || ticket.revertedByTicketId) && (
        <p className="flex flex-wrap gap-4 text-sm">
          {ticket.revertsTicketId && (
            <Link className="text-primary underline underline-offset-2" href={`/tickets/${ticket.revertsTicketId}`}>
              Reverts ticket
            </Link>
          )}
          {ticket.revertedByTicketId && (
            <Link className="text-primary underline underline-offset-2" href={`/tickets/${ticket.revertedByTicketId}`}>
              Reverted by ticket
            </Link>
          )}
        </p>
      )}
      <ConflictList conflicts={conflicts} />
      <ReviewList ticket={ticket} />

      {actionError !== null && !blocked && <ErrorAlert error={actionError} title="That did not work" />}
      <ActionBar
        ticket={ticket}
        me={me}
        busy={apply.isPending || withdraw.isPending}
        handlers={{
          onEdit: () => setDialog("edit"),
          onApply: () => void run(() => apply.mutateAsync(), "Ticket applied"),
          onSubmit: () => setDialog("submit"),
          onWithdraw: () => void run(() => withdraw.mutateAsync(), "Ticket withdrawn"),
          onReview: () => setDialog("review"),
          onEscalate: () => setDialog("escalate"),
          onRevert: () => setDialog("revert"),
        }}
      />

      {dialog === "edit" && <EditTicketDialog ticket={ticket} open onOpenChange={(open) => !open && setDialog(null)} />}
      {dialog === "submit" && <SubmitDialog ticket={ticket} open onOpenChange={(open) => !open && setDialog(null)} />}
      {dialog === "review" && actions.review && (
        <ReviewDialog ticket={ticket} decisions={actions.review.decisions} open onOpenChange={(open) => !open && setDialog(null)} />
      )}
      {dialog === "escalate" && <EscalateDialog ticket={ticket} open onOpenChange={(open) => !open && setDialog(null)} />}
      {dialog === "revert" && <RevertDialog ticket={ticket} open onOpenChange={(open) => !open && setDialog(null)} />}
    </section>
  );
}

function BackLink() {
  return (
    <p>
      <Link href="/tickets" className="text-sm font-medium text-primary underline underline-offset-2">
        ← All tickets
      </Link>
    </p>
  );
}
