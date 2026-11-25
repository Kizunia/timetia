"use client";

import Link from "next/link";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Skeleton } from "@/components/ui/skeleton";
import { useMyPlaces } from "@/features/campus/useMyPlaces";
import { usePermissions } from "@/features/identity/permissions";
import { useIssue, type Issue } from "@/features/issues/api";
import { IssueActionBar } from "@/features/issues/components/IssueActionBar";
import { IssueStatusBadge } from "@/features/issues/components/IssueTable";
import { issueActions } from "@/features/issues/lib/actions";
import { audienceOf } from "@/features/issues/lib/audience";

/** One issue: what was asked, which teachers it names, the tickets that answer it, and what the person may do with it. */
export function IssueDetailScreen({ id }: { id: string }) {
  const issue = useIssue(id);
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
      {issue.data && <IssueBody issue={issue.data} />}
    </section>
  );
}

function IssueBody({ issue }: { issue: Issue }) {
  const { me, hasAny } = usePermissions();
  const { facultyIds } = useMyPlaces();
  const canSeeTickets = hasAny("rescheduling.tickets.view", "rescheduling.tickets.view-in-scope");
  const mentioned = issue.mentionedFaculty.map((f) => f.name ?? "a removed teacher").join(", ");

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 id="issue-heading" className="text-2xl font-semibold tracking-tight">
          {issue.title}
        </h1>
        <IssueStatusBadge status={issue.status} />
      </div>
      <p className="text-sm text-muted-foreground">
        {audienceOf(issue)} · opened by {issue.openerName ?? "a removed user"} on {new Date(issue.createdAt).toLocaleString()}
      </p>
      {issue.description && <p className="whitespace-pre-wrap">{issue.description}</p>}
      <p className="text-sm">Concerns: {mentioned || "no one in particular"}</p>
      {issue.linkedTicketIds.length > 0 && (
        <p className="text-sm">
          Answered by:{" "}
          {issue.linkedTicketIds.map((ticketId) =>
            canSeeTickets ? (
              <Link key={ticketId} href={`/tickets/${ticketId}`} className="mr-2 text-primary underline underline-offset-2">
                ticket {ticketId.slice(-6)}
              </Link>
            ) : (
              <span key={ticketId} className="mr-2">
                ticket {ticketId.slice(-6)}
              </span>
            ),
          )}
        </p>
      )}
      {issue.closedAt && (
        <p className="text-sm text-muted-foreground">
          Closed by {issue.closedByName ?? "a removed user"} on {new Date(issue.closedAt).toLocaleString()}
        </p>
      )}
      <IssueActionBar issue={issue} actions={issueActions(issue, me.id, facultyIds)} />
    </>
  );
}
