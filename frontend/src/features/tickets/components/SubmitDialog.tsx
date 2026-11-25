"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Skeleton } from "@/components/ui/skeleton";
import { useReviewerCandidates, useSubmitTicket, type Ticket } from "@/features/tickets/api";
import { ReviewerPicker } from "@/features/tickets/components/ReviewerPicker";
import { hasHod, toReviewers } from "@/features/tickets/lib/reviewers";
import { submitSchema } from "@/features/tickets/schemas";

/** Sends a draft for review to the people the author picks. A permanent change cannot go without an HOD. */
export function SubmitDialog({ ticket, open, onOpenChange }: { ticket: Ticket; open: boolean; onOpenChange: (open: boolean) => void }) {
  const candidates = useReviewerCandidates(ticket.id, open);
  const submit = useSubmitTicket(ticket.id);
  const [chosen, setChosen] = useState<string[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const requireHod = ticket.type === "PERMANENT_CHANGE";
  const people = candidates.data ?? [];

  const send = async () => {
    const parsed = submitSchema.safeParse({ reviewerIds: chosen });
    if (!parsed.success) return setProblem(parsed.error.issues[0]?.message ?? "Choose at least one person");
    if (requireHod && !hasHod(people, chosen)) return setProblem("A permanent change needs an HOD. Choose one.");
    setProblem(null);
    try {
      await submit.mutateAsync({ reviewers: toReviewers(people, chosen) });
      toast.success("Sent for review");
      onOpenChange(false);
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Send for review</DialogTitle>
          <DialogDescription>
            Choose who should look at this ticket. It is applied when everyone you pick has answered and nobody has rejected it.
          </DialogDescription>
        </DialogHeader>
        {candidates.isPending && <Skeleton className="h-24 w-full" aria-label="Loading the people you can ask" />}
        {candidates.isError && <ErrorAlert error={candidates.error} title="The people to ask could not be loaded" />}
        {candidates.data && <ReviewerPicker candidates={people} value={chosen} onChange={setChosen} requireHod={requireHod} />}
        {problem && (
          <p role="alert" className="text-sm text-destructive">
            {problem}
          </p>
        )}
        {submit.isError && <ErrorAlert error={submit.error} title="The ticket was not sent" />}
        <DialogFooter>
          <Button variant="outline" onClick={() => onOpenChange(false)}>
            Cancel
          </Button>
          <Button onClick={() => void send()} disabled={submit.isPending || !candidates.data}>
            {submit.isPending ? "Sending…" : "Send for review"}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
