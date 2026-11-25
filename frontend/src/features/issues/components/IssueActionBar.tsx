"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { useCloseIssue, type Issue } from "@/features/issues/api";
import { LinkTicketDialog } from "@/features/issues/components/LinkTicketDialog";
import { MentionDialog } from "@/features/issues/components/MentionDialog";
import type { IssueActions } from "@/features/issues/lib/actions";

/** The buttons of an open issue that this person may use. */
export function IssueActionBar({ issue, actions }: { issue: Issue; actions: IssueActions }) {
  const close = useCloseIssue(issue.id);
  const [dialog, setDialog] = useState<"mention" | "link" | "close" | null>(null);
  const done = () => setDialog(null);

  if (!actions.close && !actions.link && !actions.mention) return null;

  const closeIssue = async () => {
    try {
      await close.mutateAsync();
      toast.success("Issue closed");
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <div className="grid gap-3">
      {close.isError && <ErrorAlert error={close.error} title="The issue was not closed" />}
      <div className="flex flex-wrap gap-2">
        {actions.mention && (
          <Button variant="outline" onClick={() => setDialog("mention")}>
            Mention a teacher
          </Button>
        )}
        {actions.link && (
          <Button variant="outline" onClick={() => setDialog("link")}>
            Link a ticket
          </Button>
        )}
        {actions.close && (
          <Button variant="outline" disabled={close.isPending} onClick={() => setDialog("close")}>
            Close the issue
          </Button>
        )}
      </div>

      {dialog === "mention" && <MentionDialog issue={issue} onClose={done} />}
      {dialog === "link" && <LinkTicketDialog issue={issue} onClose={done} />}
      <AlertDialog open={dialog === "close"} onOpenChange={(next) => !next && done()}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Close this issue?</AlertDialogTitle>
            <AlertDialogDescription>A closed issue cannot be opened again. Closing it does not change the timetable.</AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => void closeIssue()}>Close issue</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
