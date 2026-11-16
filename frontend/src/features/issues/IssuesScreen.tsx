"use client";

import Link from "next/link";
import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { Can } from "@/features/identity/permissions";
import { useIssues } from "@/features/issues/api";
import { IssueForm } from "@/features/issues/IssueForm";
import { SESSION_LABEL } from "@/lib/labels";

/** The issues the person may see: what classes ask for, which teachers they concern, and whether they are still open. */
export function IssuesScreen() {
  const [page, setPage] = useState(0);
  const [opening, setOpening] = useState(false);
  const issues = useIssues({ page, size: 20 });
  return (
    <section aria-labelledby="issues-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="issues-heading" className="text-2xl font-semibold tracking-tight">
          Issues
        </h1>
        <Can permission="rescheduling.issues.open">{!opening && <Button onClick={() => setOpening(true)}>Open an issue</Button>}</Can>
      </div>
      {opening && <IssueForm onDone={() => setOpening(false)} />}
      {issues.isError && <ErrorAlert error={issues.error} title="The issues could not be loaded" />}
      {issues.isPending && <Skeleton className="h-24 w-full" aria-label="Loading issues" />}
      {issues.data && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Title</TableHead>
                <TableHead>About</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Opened</TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {issues.data.items.map((issue) => (
                <TableRow key={issue.id} data-testid="issue-row">
                  <TableCell>
                    <Link href={`/issues/${issue.id}`} className="font-medium text-primary underline underline-offset-2">
                      {issue.title}
                    </Link>
                  </TableCell>
                  <TableCell>{SESSION_LABEL[issue.sessionType]}</TableCell>
                  <TableCell>
                    <Badge variant={issue.status === "OPEN" ? "secondary" : "ghost"}>{issue.status === "OPEN" ? "Open" : "Closed"}</Badge>
                  </TableCell>
                  <TableCell>{new Date(issue.createdAt).toLocaleDateString()}</TableCell>
                </TableRow>
              ))}
              {issues.data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={4} className="text-center text-muted-foreground">
                    There are no issues yet.
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <Pager
            page={issues.data.page.number}
            totalPages={issues.data.page.totalPages}
            totalElements={issues.data.page.totalElements}
            onPage={setPage}
          />
        </>
      )}
    </section>
  );
}
