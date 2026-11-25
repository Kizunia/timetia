"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import {
  AlertDialog,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useRevertTicket, type Ticket } from "@/features/tickets/api";

/**
 * Undoes an applied ticket. This does not change the timetable by itself: it raises a new draft ticket that puts the lecture
 * back, which is applied like any other, so the person is taken to it.
 */
export function RevertDialog({ ticket, open, onOpenChange }: { ticket: Ticket; open: boolean; onOpenChange: (open: boolean) => void }) {
  const router = useRouter();
  const revert = useRevertTicket(ticket.id);
  const [reason, setReason] = useState("");

  const send = async () => {
    try {
      const created = await revert.mutateAsync({ reason: reason.trim() ? reason.trim() : undefined });
      toast.success("Revert raised. Apply it to put the lecture back.");
      onOpenChange(false);
      router.push(`/tickets/${created.id}`);
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Revert this ticket?</AlertDialogTitle>
          <AlertDialogDescription>
            This raises a new ticket that puts the lecture back where, and with whom, it was. You apply that ticket to make the
            change.
          </AlertDialogDescription>
        </AlertDialogHeader>
        <div className="grid gap-2">
          <Label htmlFor="revert-reason">Why? (optional)</Label>
          <Textarea id="revert-reason" rows={3} value={reason} onChange={(event) => setReason(event.target.value)} />
        </div>
        {revert.isError && <ErrorAlert error={revert.error} title="The revert was not raised" />}
        <AlertDialogFooter>
          <AlertDialogCancel>Cancel</AlertDialogCancel>
          <Button onClick={() => void send()} disabled={revert.isPending}>
            {revert.isPending ? "Raising…" : "Raise the revert"}
          </Button>
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
