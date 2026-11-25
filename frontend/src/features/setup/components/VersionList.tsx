"use client";

import Link from "next/link";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useDeleteVersion, useRows, useVersions, type Version } from "@/features/setup/api";
import { FieldSelect } from "@/features/setup/components/FieldSelect";
import { NewVersionDialog } from "@/features/setup/components/NewVersionDialog";
import { formatDay } from "@/lib/dates";

const ALL = "all";

export function VersionList() {
  const [page, setPage] = useState(0);
  const [division, setDivision] = useState(ALL);
  const [status, setStatus] = useState(ALL);
  const [creating, setCreating] = useState(false);
  const divisions = useRows("divisions");
  const terms = useRows("academic-terms");
  const versions = useVersions({
    page,
    ...(division !== ALL ? { division } : {}),
    ...(status !== ALL ? { status: status as Version["status"] } : {}),
  });
  const remove = useDeleteVersion();

  const codeIn = (rows: typeof divisions.data, id: string) => rows?.find((row) => row.id === id)?.code ?? "—";

  const deleteDraft = async (id: string) => {
    try {
      await remove.mutateAsync(id);
      toast.success("Draft deleted");
    } catch {
      // The alert above the table shows what the server said.
    }
  };

  return (
    <section aria-labelledby="timetables-heading" className="grid grid-cols-1 gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="timetables-heading" className="text-2xl font-semibold tracking-tight">
          Timetables
        </h1>
        <Button onClick={() => setCreating(true)}>New version</Button>
      </div>
      <div className="flex flex-wrap gap-3">
        <FieldSelect
          id="version-division"
          label="Division"
          value={division}
          options={[{ value: ALL, label: "All divisions" }, ...(divisions.data ?? []).map((d) => ({ value: d.id, label: d.code ?? "" }))]}
          onChange={(value) => {
            setDivision(value);
            setPage(0);
          }}
        />
        <FieldSelect
          id="version-status"
          label="Status"
          value={status}
          options={[
            { value: ALL, label: "All" },
            { value: "DRAFT", label: "Draft" },
            { value: "PUBLISHED", label: "Published" },
          ]}
          onChange={(value) => {
            setStatus(value);
            setPage(0);
          }}
        />
      </div>

      {versions.isError && <ErrorAlert error={versions.error} title="The timetables could not be loaded" />}
      {remove.isError && <ErrorAlert error={remove.error} title="The draft was not deleted" />}
      {versions.isPending && <Skeleton className="h-24 w-full" aria-label="Loading timetables" />}
      {versions.data && (
        <>
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Division</TableHead>
                <TableHead>Term</TableHead>
                <TableHead>Effective from</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>
                  <span className="sr-only">Actions</span>
                </TableHead>
              </TableRow>
            </TableHeader>
            <TableBody>
              {versions.data.items.map((version) => (
                <TableRow key={version.id} data-testid="version-row">
                  <TableCell>{codeIn(divisions.data, version.divisionId)}</TableCell>
                  <TableCell>{codeIn(terms.data, version.termId)}</TableCell>
                  <TableCell>{formatDay(version.effectiveFrom)}</TableCell>
                  <TableCell>
                    <Badge variant={version.status === "PUBLISHED" ? "default" : "outline"}>
                      {version.status === "PUBLISHED" ? "Published" : "Draft"}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-right">
                    <Button asChild variant="ghost" size="sm">
                      <Link href={`/admin/timetables?version=${version.id}`}>Open</Link>
                    </Button>
                    {version.status === "DRAFT" && (
                      <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={() => void deleteDraft(version.id)}>
                        Delete draft
                      </Button>
                    )}
                  </TableCell>
                </TableRow>
              ))}
              {versions.data.items.length === 0 && (
                <TableRow>
                  <TableCell colSpan={5} className="text-center text-muted-foreground">
                    No timetables yet
                  </TableCell>
                </TableRow>
              )}
            </TableBody>
          </Table>
          <Pager
            page={versions.data.page.number}
            totalPages={versions.data.page.totalPages}
            totalElements={versions.data.page.totalElements}
            onPage={setPage}
          />
        </>
      )}
      {creating && <NewVersionDialog divisions={divisions.data ?? []} terms={terms.data ?? []} onClose={() => setCreating(false)} />}
    </section>
  );
}
