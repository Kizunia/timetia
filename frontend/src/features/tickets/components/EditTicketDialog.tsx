"use client";

import { useQueryClient } from "@tanstack/react-query";
import { useState } from "react";
import { toast } from "sonner";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useEditTicket, type Ticket } from "@/features/tickets/api";
import { TicketForm } from "@/features/tickets/components/TicketForm";
import { buildEditBody, type TicketFormValues, type TicketKind } from "@/features/tickets/lib/request";
import { lectureHours, slotDefaults } from "@/features/tickets/lib/slot";
import { ApiError } from "@/lib/api/problem";

/** The values the edit form starts with: the plan as the ticket holds it. */
function defaultsOfTicket(ticket: Ticket): TicketFormValues {
  const target = ticket.change.target;
  const slot = target
    ? slotDefaults(target)
    : { date: "", startHour: 9, endHour: 10, mode: "IN_PERSON" as const };
  return {
    ...slot,
    description: ticket.description ?? "",
    reason: ticket.change.reason ?? "",
    substituteFacultyId: ticket.change.substituteFacultyId ?? undefined,
  };
}

/**
 * Changes the plan of a ticket that is not applied. The whole plan is sent again with the version that was read, so if
 * someone changed the ticket meanwhile the server refuses and this offers to load it again. An exchange keeps its two lectures
 * and can change only its note; a permanent change or a revert cannot be edited.
 */
export function EditTicketDialog({ ticket, open, onOpenChange }: { ticket: Ticket; open: boolean; onOpenChange: (open: boolean) => void }) {
  const edit = useEditTicket(ticket.id);
  const client = useQueryClient();
  const [stale, setStale] = useState(false);
  const kind = ticket.type as TicketKind;
  const lecture = ticket.change.lecture;

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>Edit the ticket</DialogTitle>
          <DialogDescription>
            {ticket.status === "REJECTED"
              ? "Editing a rejected ticket makes it a draft again, in a new round."
              : "The ticket is checked for conflicts again when you save."}
          </DialogDescription>
        </DialogHeader>
        <TicketForm
          kind={kind}
          descriptionOnly={ticket.type === "EXCHANGE"}
          context={{
            sessionType: ticket.sessionType,
            // A move keeps the lecture's length; an extra lecture chooses its own.
            lockedHours: ticket.type === "RESCHEDULE" && lecture ? lectureHours({ startTime: lecture.startTime, endTime: lecture.endTime }) : undefined,
            excludeLecture: ticket.change.lectureId ?? undefined,
            teacherFacultyId: ticket.facultyId,
          }}
          defaults={defaultsOfTicket(ticket)}
          submitLabel="Save changes"
          onCancel={() => onOpenChange(false)}
          onSubmit={async (values) => {
            setStale(false);
            try {
              await edit.mutateAsync(buildEditBody(ticket.type, ticket.version, values));
            } catch (error) {
              if (error instanceof ApiError && error.code === "rescheduling.stale_ticket") setStale(true);
              throw error;
            }
            toast.success("Ticket saved");
            onOpenChange(false);
          }}
        />
        {stale && (
          <Alert>
            <AlertDescription className="flex flex-wrap items-center justify-between gap-2">
              The ticket was changed after you opened it.
              <Button
                size="sm"
                variant="outline"
                onClick={async () => {
                  await client.invalidateQueries({ queryKey: ["ticket", ticket.id] });
                  onOpenChange(false);
                }}
              >
                Reload
              </Button>
            </AlertDescription>
          </Alert>
        )}
      </DialogContent>
    </Dialog>
  );
}
