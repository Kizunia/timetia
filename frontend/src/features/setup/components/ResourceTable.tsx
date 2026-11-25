"use client";

import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useRows, type Row } from "@/features/setup/api";
import { CreateDialog } from "@/features/setup/components/CreateDialog";
import { FieldSelect } from "@/features/setup/components/FieldSelect";
import { LinkAccountDialog } from "@/features/setup/components/LinkAccountDialog";
import { DeleteDialog, RenameDialog } from "@/features/setup/components/RowDialogs";
import { useLookups } from "@/features/setup/lookups";
import type { Resource } from "@/features/setup/resources";

const ALL = "all";

type Open = { kind: "create" } | { kind: "rename" | "delete" | "link"; row: Row } | null;

/** One campus list with its search, filter and the actions on its rows. The same table serves all eight lists. */
export function ResourceTable({ resource }: { resource: Resource }) {
  const lookups = useLookups();
  const [q, setQ] = useState("");
  const [filter, setFilter] = useState(ALL);
  const [open, setOpen] = useState<Open>(null);
  const rows = useRows(resource.key, {
    ...(resource.named && q.trim() ? { q: q.trim() } : {}),
    ...(resource.filter && filter !== ALL ? { [resource.filter.name]: filter } : {}),
  });
  const close = () => setOpen(null);
  const columns = resource.columns.length + 1;

  return (
    <div className="grid grid-cols-1 gap-4">
      <div className="flex flex-wrap items-end justify-between gap-3">
        <div className="flex flex-wrap items-end gap-3">
          {resource.named && (
            <div className="grid gap-2">
              <Label htmlFor="setup-search">Search code or name</Label>
              <Input id="setup-search" value={q} maxLength={100} onChange={(event) => setQ(event.target.value)} />
            </div>
          )}
          {resource.filter && (
            <FieldSelect
              id="setup-filter"
              label={resource.filter.label}
              value={filter}
              options={[{ value: ALL, label: "All" }, ...lookups.options(resource.filter.options)]}
              onChange={setFilter}
            />
          )}
        </div>
        <Button onClick={() => setOpen({ kind: "create" })}>New {resource.one}</Button>
      </div>

      {rows.isError && <ErrorAlert error={rows.error} title="The list could not be loaded" />}
      {rows.isPending && <Skeleton className="h-24 w-full" aria-label="Loading" />}
      {rows.data && (
        <Table>
          <TableHeader>
            <TableRow>
              {resource.columns.map((column) => (
                <TableHead key={column.header}>{column.header}</TableHead>
              ))}
              <TableHead>
                <span className="sr-only">Actions</span>
              </TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {rows.data.map((row) => {
              const label = row.code ?? lookups.name("subjects", row.subjectId);
              return (
                <TableRow key={row.id} data-testid="setup-row">
                  {resource.columns.map((column) => (
                    <TableCell key={column.header}>{column.cell(row, lookups)}</TableCell>
                  ))}
                  <TableCell className="text-right">
                    {resource.key === "faculties" && (
                      <Button variant="ghost" size="sm" aria-label={`Account of ${label}`} onClick={() => setOpen({ kind: "link", row })}>
                        Account
                      </Button>
                    )}
                    {resource.named && (
                      <Button variant="ghost" size="sm" aria-label={`Rename ${label}`} onClick={() => setOpen({ kind: "rename", row })}>
                        Rename
                      </Button>
                    )}
                    <Button variant="ghost" size="sm" aria-label={`Delete ${label}`} onClick={() => setOpen({ kind: "delete", row })}>
                      Delete
                    </Button>
                  </TableCell>
                </TableRow>
              );
            })}
            {rows.data.length === 0 && (
              <TableRow>
                <TableCell colSpan={columns} className="text-center text-muted-foreground">
                  Nothing here yet
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}

      {open?.kind === "create" && <CreateDialog resource={resource} lookups={lookups} onClose={close} />}
      {open?.kind === "rename" && <RenameDialog resource={resource} row={open.row} onClose={close} />}
      {open?.kind === "delete" && <DeleteDialog resource={resource} row={open.row} onClose={close} />}
      {open?.kind === "link" && <LinkAccountDialog faculty={open.row} onClose={close} />}
    </div>
  );
}
