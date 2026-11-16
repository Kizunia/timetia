"use client";

import Link from "next/link";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { useFaculties } from "@/features/campus/api";
import { nameIndex } from "@/features/campus/lib/names";
import { useCloseIssue, useIssue } from "@/features/issues/api";
import { SESSION_LABEL } from "@/lib/labels";

/** One issue: what was asked, which teachers it names, the tickets that answer it, and a Close button. */
export function IssueDetailScreen({ id }: { id: string }) {
  const issue = useIssue(id);
  const close = useCloseIssue(id);
  const faculties = useFaculties();
  const name = nameIndex(faculties.data, (f) => f.name);
  const data = issue.data;
  return (
    <section aria-labelledby="issue-heading" className="grid gap-4">
      <p>
        <Link href="/issues" className="text-sm font-medium text-primary underline underline-offset-2">
          ← All issues
        </Link>
      </p>
      {issue.isError && (
        <>
          <h1 id="issue-heading" className="sr-only">
            Issue
          </h1>
          <ErrorAlert error={issue.error} title="The issue could not be opened" />
        </>
      )}
      {issue.isPending && <Skeleton className="h-32 w-full" aria-label="Loading the issue" />}
      {data && (
        <>
          <div className="flex flex-wrap items-center gap-3">
            <h1 id="issue-heading" className="text-2xl font-semibold tracking-tight">
              {data.title}
            </h1>
            <Badge variant={data.status === "OPEN" ? "secondary" : "ghost"}>{data.status === "OPEN" ? "Open" : "Closed"}</Badge>
          </div>
          <p className="text-sm text-muted-foreground">
            {SESSION_LABEL[data.sessionType]} issue, opened {new Date(data.createdAt).toLocaleString()}.
          </p>
          {data.description && <p>{data.description}</p>}
          <p className="text-sm">
            Concerns: {data.mentionedFacultyIds.length ? data.mentionedFacultyIds.map(name).join(", ") : "no one in particular"}
          </p>
          {data.linkedTicketIds.length > 0 && (
            <p className="text-sm">
              Answered by:{" "}
              {data.linkedTicketIds.map((t) => (
                <Link key={t} href={`/tickets/${t}`} className="mr-2 text-primary underline underline-offset-2">
                  ticket
                </Link>
              ))}
            </p>
          )}
          {close.isError && <ErrorAlert error={close.error} title="The issue was not closed" />}
          {data.status === "OPEN" && (
            <div>
              <Button
                variant="outline"
                disabled={close.isPending}
                onClick={async () => {
                  try {
                    await close.mutateAsync();
                    toast.success("Issue closed");
                  } catch {
                    // The alert above shows what the server said.
                  }
                }}
              >
                Close the issue
              </Button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
