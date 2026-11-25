"use client";

import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { type Person, useAuditEntries } from "@/features/audit/api";

const TARGETS = [
  { value: "ALL", label: "Anything" },
  { value: "user", label: "Users" },
  { value: "role", label: "Roles" },
  { value: "note", label: "Notes" }, // module:notes
];

/**
 * A person in an entry. The log stores only user IDs, so the name is looked up when the entry is shown, and a user who was
 * erased can only be shown as erased (ADR 0046).
 */
export function PersonLabel({ person }: { person: Person }) {
  if (person.kind === "SYSTEM") return <span className="text-muted-foreground">System</span>;
  if (person.kind === "ERASED") {
    return (
      <span className="text-muted-foreground" title={person.id}>
        Deleted user
      </span>
    );
  }
  return <span>{person.name}</span>;
}

/** The audit log, newest first, with a filter by action and by kind of record. Needs audit.entries.view. */
export function AuditScreen() {
  const [page, setPage] = useState(0);
  const [prefix, setPrefix] = useState("");
  const [action, setAction] = useState("");
  const [target, setTarget] = useState("ALL");
  const entries = useAuditEntries({
    page,
    size: 25,
    action: action || undefined,
    targetType: target === "ALL" ? undefined : target,
  });

  return (
    <section aria-labelledby="audit-heading">
      <h1 id="audit-heading" className="text-2xl font-semibold tracking-tight">
        Audit log
      </h1>
      <p className="mt-2 max-w-prose text-sm text-muted-foreground">
        Every administrative change, newest first. People appear by their current name; a user who was erased shows as a
        deleted user. Sign-ins are in Keycloak.
      </p>

      <form
        role="search"
        className="mt-6 flex flex-wrap items-end gap-3"
        onSubmit={(event) => {
          event.preventDefault();
          setAction(prefix.trim());
          setPage(0);
        }}
      >
        <div className="grid gap-1.5">
          <Label htmlFor="audit-action">Action starts with</Label>
          <Input id="audit-action" value={prefix} onChange={(event) => setPrefix(event.target.value)} placeholder="access. or identity.user." className="w-64" />
        </div>
        <div className="grid gap-1.5">
          <Label htmlFor="audit-target">About</Label>
          <Select
            value={target}
            onValueChange={(value) => {
              setTarget(value);
              setPage(0);
            }}
          >
            <SelectTrigger id="audit-target" className="w-40">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {TARGETS.map((option) => (
                <SelectItem key={option.value} value={option.value}>
                  {option.label}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <Button type="submit" variant="secondary">
          Filter
        </Button>
      </form>

      <div className="mt-6">
        {entries.isError && <ErrorAlert error={entries.error} title="The audit log could not be loaded" />}
        {entries.isPending && <Skeleton className="h-40 w-full" aria-label="Loading the audit log" />}
        {entries.data && (
          <>
            <Table>
              <TableHeader>
                <TableRow>
                  <TableHead>When</TableHead>
                  <TableHead>Who</TableHead>
                  <TableHead>What</TableHead>
                  <TableHead>About</TableHead>
                  <TableHead>Details</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {entries.data.items.map((entry) => (
                  <TableRow key={entry.id} data-testid="audit-row">
                    <TableCell className="whitespace-nowrap">
                      <time dateTime={entry.occurredAt}>{new Date(entry.occurredAt).toLocaleString()}</time>
                    </TableCell>
                    <TableCell>
                      <PersonLabel person={entry.actor} />
                    </TableCell>
                    <TableCell>
                      <code className="text-xs">{entry.action}</code>{" "}
                      {entry.outcome !== "success" && <Badge variant="destructive">{entry.outcome}</Badge>}
                    </TableCell>
                    <TableCell>
                      {entry.target ? (
                        <PersonLabel person={entry.target} />
                      ) : (
                        <span className="text-muted-foreground">
                          {entry.targetType} {entry.targetId?.slice(0, 8)}
                        </span>
                      )}
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {Object.entries(entry.changes).map(([field, value]) => `${field}: ${value}`).join(" · ")}
                    </TableCell>
                  </TableRow>
                ))}
                {entries.data.items.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5} className="text-center text-muted-foreground">
                      Nothing matches.
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
            <Pager
              page={entries.data.page.number}
              totalPages={entries.data.page.totalPages}
              totalElements={entries.data.page.totalElements}
              onPage={setPage}
            />
          </>
        )}
      </div>
    </section>
  );
}
