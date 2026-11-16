"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Label } from "@/components/ui/label";
import { Textarea } from "@/components/ui/textarea";
import { useReviewTicket, type Ticket } from "@/features/tickets/api";
import type { Decision } from "@/features/tickets/lib/actions";
import { reviewSchema, type ReviewValues } from "@/features/tickets/schemas";
import { applyProblem } from "@/lib/forms";

const DECISION_LABEL: Record<Decision, string> = {
  APPROVE: "Approve",
  ACKNOWLEDGE: "Acknowledge (I have seen it and do not object)",
  REJECT: "Reject",
};

/** What to tell the person once they have answered: the last answer may apply the ticket or send it back. */
function outcome(decision: Decision, status: Ticket["status"]): string {
  if (status === "APPLIED") return "Ticket applied";
  if (status === "REJECTED") return "Rejected";
  return decision === "ACKNOWLEDGE" ? "Acknowledged" : "Approved";
}

/** Answers a review the person was asked for. A rejection needs a reason; an HOD cannot merely acknowledge a permanent change. */
export function ReviewDialog({
  ticket,
  decisions,
  open,
  onOpenChange,
}: {
  ticket: Ticket;
  decisions: readonly Decision[];
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const review = useReviewTicket(ticket.id);
  const [failure, setFailure] = useState<string | null>(null);
  const form = useForm<ReviewValues, unknown, ReviewValues>({
    resolver: zodResolver(reviewSchema),
    defaultValues: { decision: decisions[0] ?? "APPROVE", reason: "" },
  });
  const decision = form.watch("decision");

  const submit = form.handleSubmit(async (values) => {
    setFailure(null);
    try {
      const answered = await review.mutateAsync({
        decision: values.decision,
        reason: values.reason?.trim() ? values.reason.trim() : undefined,
      });
      toast.success(outcome(values.decision, answered.status));
      onOpenChange(false);
    } catch (error) {
      setFailure(applyProblem(error, ["reason", "decision"], (field, message) => form.setError(field as "reason", { message })));
    }
  });

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>Review this ticket</DialogTitle>
          <DialogDescription>Your answer is recorded. The last answer that completes the review applies the ticket.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="grid gap-4">
          <fieldset className="grid gap-2">
            <legend className="mb-1 text-sm font-medium">Your answer</legend>
            {decisions.map((option) => (
              <label key={option} className="flex items-center gap-2 text-sm">
                <input type="radio" value={option} {...form.register("decision")} />
                {DECISION_LABEL[option]}
              </label>
            ))}
          </fieldset>
          {(decision === "REJECT" || form.formState.errors.reason) && (
            <div className="grid gap-2">
              <Label htmlFor="review-reason">Why do you reject it?</Label>
              <Textarea
                id="review-reason"
                rows={3}
                aria-invalid={form.formState.errors.reason ? true : undefined}
                aria-describedby={form.formState.errors.reason ? "review-reason-error" : undefined}
                {...form.register("reason")}
              />
              {form.formState.errors.reason && (
                <p id="review-reason-error" role="alert" className="text-sm text-destructive">
                  {form.formState.errors.reason.message}
                </p>
              )}
            </div>
          )}
          {failure && <ErrorAlert error={review.error} title="Your answer was not recorded" />}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={review.isPending}>
              {review.isPending ? "Sending…" : "Send answer"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
