"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useEscalateTicket, useReviewerCandidates, type Ticket } from "@/features/tickets/api";

/** Takes a rejected ticket to one HOD, who decides. Only HODs are offered, and only one can be chosen. */
export function EscalateDialog({ ticket, open, onOpenChange }: { ticket: Ticket; open: boolean; onOpenChange: (open: boolean) => void }) {
  const candidates = useReviewerCandidates(ticket.id, open);
  const escalate = useEscalateTicket(ticket.id);
  const [chosen, setChosen] = useState<string | null>(null);
  const hods = (candidates.data ?? []).filter((candidate) => candidate.capacity === "HOD");

  const send = async () => {
    if (!chosen) return;
    try {
      await escalate.mutateAsync({ hodUserId: chosen });
      toast.success("Taken to the HOD");
      onOpenChange(false);
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Take it to the HOD</DialogTitle>
          <DialogDescription>The HOD looks at the ticket again and decides. Choose which HOD to ask.</DialogDescription>
        </DialogHeader>
        {candidates.isPending && <Skeleton className="h-16 w-full" aria-label="Loading the HODs" />}
        {candidates.isError && <ErrorAlert error={candidates.error} title="The HODs could not be loaded" />}
        {candidates.data && hods.length === 0 && (
          <p className="text-sm text-muted-foreground">No HOD is available for this department. Ask an administrator.</p>
        )}
        {hods.length > 0 && (
          <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-medium">HOD</legend>
            {hods.map((hod) => (
              <label key={hod.userId} className="flex items-center gap-2 text-sm">
                <input type="radio" name="hod" value={hod.userId} checked={chosen === hod.userId} onChange={() => setChosen(hod.userId)} />
                {hod.name}
              </label>
            ))}
          </fieldset>
        )}
        {escalate.isError && <ErrorAlert error={escalate.error} title="The ticket was not taken to the HOD" />}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={() => void send()} disabled={!chosen || escalate.isPending}>
            {escalate.isPending ? "Sending…" : "Take it to the HOD"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
