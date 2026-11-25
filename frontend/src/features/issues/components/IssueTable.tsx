import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { Issue } from "@/features/issues/api";
import { audienceOf } from "@/features/issues/lib/audience";

export function IssueStatusBadge({ status }: { status: Issue["status"] }) {
  return <Badge variant={status === "OPEN" ? "secondary" : "ghost"}>{status === "OPEN" ? "Open" : "Closed"}</Badge>;
}

export function IssueTable({ issues, empty }: { issues: Issue[]; empty: string }) {
  return (
    <Table>
      <TableHeader>
        <TableRow>
          <TableHead>Title</TableHead>
          <TableHead>Audience</TableHead>
          <TableHead>Status</TableHead>
          <TableHead>Opened by</TableHead>
          <TableHead>Mentions</TableHead>
          <TableHead>Created</TableHead>
        </TableRow>
      </TableHeader>
      <TableBody>
        {issues.map((issue) => (
          <TableRow key={issue.id} data-testid="issue-row">
            <TableCell>
              <Link href={`/issues/${issue.id}`} className="font-medium text-primary underline underline-offset-2">
                {issue.title}
              </Link>
            </TableCell>
            <TableCell>{audienceOf(issue)}</TableCell>
            <TableCell>
              <IssueStatusBadge status={issue.status} />
            </TableCell>
            <TableCell>{issue.openerName ?? "—"}</TableCell>
            <TableCell>{issue.mentionedFacultyIds.length}</TableCell>
            <TableCell>{new Date(issue.createdAt).toLocaleDateString()}</TableCell>
          </TableRow>
        ))}
        {issues.length === 0 && (
          <TableRow>
            <TableCell colSpan={6} className="text-center text-muted-foreground">
              {empty}
            </TableCell>
          </TableRow>
        )}
      </TableBody>
    </Table>
  );
}
