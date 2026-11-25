"use client";

import Link from "next/link";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { useDeleteEntry, useEntries, useRows, useVersion, type Version } from "@/features/setup/api";
import { AddEntryForm } from "@/features/setup/components/AddEntryForm";
import { PublishButton } from "@/features/setup/components/PublishButton";
import { useLookups } from "@/features/setup/lookups";
import { formatDay } from "@/lib/dates";
import { DAY_LABEL, SESSION_LABEL } from "@/lib/labels";

export function VersionDetail({ id }: { id: string }) {
  const version = useVersion(id);
  return (
    <section aria-labelledby="version-heading" className="grid grid-cols-1 gap-4">
      <p>
        <Link href="/admin/timetables" className="text-sm font-medium text-primary underline underline-offset-2">
          ← All timetables
        </Link>
      </p>
      {version.isError && (
        <>
          <h1 id="version-heading" className="sr-only">
            Timetable
          </h1>
          <ErrorAlert error={version.error} title="The timetable could not be opened" />
        </>
      )}
      {version.isPending && <Skeleton className="h-32 w-full" aria-label="Loading the timetable" />}
      {version.data && <Entries version={version.data} />}
    </section>
  );
}

function Entries({ version }: { version: Version }) {
  const draft = version.status === "DRAFT";
  const lookups = useLookups();
  const rooms = useRows("rooms");
  const assignments = useRows("teaching-assignments", { division: version.divisionId });
  const entries = useEntries(version.id);
  const remove = useDeleteEntry(version.id);

  /** "OOP · Lab · CSE-A1 · Dr Rao": what an entry teaches, to whom and by whom. */
  const describe = (assignmentId: string) => {
    const assignment = assignments.data?.find((row) => row.id === assignmentId);
    if (!assignment) return "—";
    const subject = lookups.options("subjects").find((option) => option.value === assignment.subjectId)?.row?.code ?? "—";
    const audience = assignment.batchId ? lookups.name("batches", assignment.batchId) : lookups.name("divisions", assignment.divisionId);
    const session = assignment.sessionType ? SESSION_LABEL[assignment.sessionType] : "—";
    return `${subject} · ${session} · ${audience} · ${lookups.name("faculties", assignment.facultyId)}`;
  };

  const sorted = [...(entries.data?.items ?? [])].sort((a, b) => a.dayOfWeek - b.dayOfWeek || a.startTime.localeCompare(b.startTime));

  return (
    <>
      <div className="flex flex-wrap items-center gap-3">
        <h1 id="version-heading" className="text-2xl font-semibold tracking-tight">
          {lookups.name("divisions", version.divisionId)} from {formatDay(version.effectiveFrom)}
        </h1>
        <Badge variant={draft ? "outline" : "default"}>{draft ? "Draft" : "Published"}</Badge>
      </div>
      {!draft && <p className="text-sm text-muted-foreground">A published timetable cannot be changed. Create a new version instead.</p>}

      {entries.isError && <ErrorAlert error={entries.error} title="The lectures could not be loaded" />}
      {remove.isError && <ErrorAlert error={remove.error} title="The lecture was not removed" />}
      {entries.isPending && <Skeleton className="h-24 w-full" aria-label="Loading lectures" />}
      {entries.data && (
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Day</TableHead>
              <TableHead>Time</TableHead>
              <TableHead>Lecture</TableHead>
              <TableHead>Where</TableHead>
              {draft && (
                <TableHead>
                  <span className="sr-only">Actions</span>
                </TableHead>
              )}
            </TableRow>
          </TableHeader>
          <TableBody>
            {sorted.map((entry) => (
              <TableRow key={entry.id} data-testid="entry-row">
                <TableCell>{DAY_LABEL[entry.dayOfWeek]}</TableCell>
                <TableCell>
                  {entry.startTime.slice(0, 5)}–{entry.endTime.slice(0, 5)}
                </TableCell>
                <TableCell>{describe(entry.assignmentId)}</TableCell>
                <TableCell>
                  {entry.mode === "ONLINE" ? "Online" : (rooms.data?.find((room) => room.id === entry.roomId)?.code ?? "—")}
                </TableCell>
                {draft && (
                  <TableCell className="text-right">
                    <Button variant="ghost" size="sm" disabled={remove.isPending} onClick={() => remove.mutate(entry.id)}>
                      Remove
                    </Button>
                  </TableCell>
                )}
              </TableRow>
            ))}
            {sorted.length === 0 && (
              <TableRow>
                <TableCell colSpan={draft ? 5 : 4} className="text-center text-muted-foreground">
                  No lectures yet
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      )}

      {draft && (
        <>
          <AddEntryForm
            versionId={version.id}
            assignments={(assignments.data ?? []).map((row) => ({ value: row.id, label: describe(row.id) }))}
            rooms={(rooms.data ?? []).map((room) => ({ value: room.id, label: room.code ?? "" }))}
          />
          <PublishButton version={version} />
        </>
      )}
    </>
  );
}
