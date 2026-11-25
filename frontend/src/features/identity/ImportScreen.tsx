"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Table, TableBody, TableCaption, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import { type UserImportLine, type UserImportReport, useUserImport } from "@/features/identity/api";
import { ApiError, fieldErrors } from "@/lib/api/problem";

/** The largest file the API accepts; larger is refused there too. This only saves sending it. */
const MAX_BYTES = 512 * 1024;

const EXAMPLE = `email,role,scope
anita.rao@college.example,HOD,CSE
ravi.kulkarni@college.example,Faculty,
sneha.patil@college.example,CR,CSE-A`;

/** What happened to a row, in words, because colour alone does not say it. A dry run says what would happen. */
function outcomeText(outcome: UserImportLine["outcome"], dryRun: boolean): string {
  switch (outcome) {
    case "ADDED":
      return dryRun ? "Would add" : "Added";
    case "ALREADY_PRESENT":
      return "Already there";
    case "ERROR":
      return "Error";
    case "SKIPPED":
      return "Skipped";
    case "NOT_PROCESSED":
      return "Not processed";
  }
}

/** Why a file was refused as a whole: the first thing its field errors say, else the generic text for the code. */
function fileMessage(error: unknown): string | undefined {
  if (!(error instanceof ApiError)) return undefined;
  return fieldErrors(error.problem).file?.message ?? undefined;
}

/**
 * Loading people from a CSV file (Timetia ADR 0006). The administrator first checks the file, which writes nothing and
 * shows what each row would do, then imports it. Sending the same file again is harmless: it only adds what is missing.
 * Only an administrator reaches this screen; the API refuses everyone else whatever the screen shows.
 */
export function ImportScreen() {
  const run = useUserImport();
  const input = useRef<HTMLInputElement>(null);
  const [file, setFile] = useState<File | null>(null);
  const [tooBig, setTooBig] = useState(false);
  // The report belongs to the file it was made for; choosing another file clears it.
  const [report, setReport] = useState<{ for: File; value: UserImportReport } | null>(null);

  const checked = report !== null && report.for === file && report.value.dryRun ? report.value : null;
  const done = report !== null && report.for === file && !report.value.dryRun ? report.value : null;
  const shown = done ?? checked;

  async function send(dryRun: boolean) {
    if (!file) return;
    try {
      const value = await run.mutateAsync({ file, dryRun });
      setReport({ for: file, value });
      if (!dryRun) toast.success("Import finished");
    } catch {
      // The alert below shows what went wrong.
    }
  }

  return (
    <section aria-labelledby="import-heading" className="grid gap-6">
      <div>
        <h1 id="import-heading" className="text-2xl font-semibold tracking-tight">
          Import people
        </h1>
        <p className="mt-1 text-muted-foreground">
          Load people and the roles they hold from a CSV file. Each new person is emailed an invitation. Sending the same
          file again changes nothing, and an import never removes or changes a role.
        </p>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>The file</CardTitle>
          <CardDescription>
            One row for each role a person holds, with the columns email, role and scope. The scope is the code of the
            department, division or batch, and stays empty for a role that applies everywhere, such as Faculty.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4">
          <pre className="overflow-x-auto rounded-md bg-muted p-3 text-sm" aria-label="Example of the file">
            {EXAMPLE}
          </pre>
          <div className="grid gap-1.5 sm:max-w-md">
            <Label htmlFor="import-file">CSV file</Label>
            <Input
              id="import-file"
              ref={input}
              type="file"
              accept=".csv,text/csv"
              aria-describedby={tooBig ? "import-file-problem" : undefined}
              onChange={(event) => {
                const chosen = event.target.files?.[0] ?? null;
                run.reset();
                setReport(null);
                setTooBig(chosen !== null && chosen.size > MAX_BYTES);
                setFile(chosen !== null && chosen.size <= MAX_BYTES ? chosen : null);
              }}
            />
            {tooBig && (
              <p id="import-file-problem" role="alert" className="text-sm text-destructive">
                That file is larger than 512 KB. Split it into smaller files.
              </p>
            )}
          </div>
          <div className="flex flex-wrap gap-2">
            <Button type="button" disabled={!file || run.isPending} onClick={() => send(true)}>
              {run.isPending && run.variables?.dryRun ? "Checking…" : "Check the file"}
            </Button>
            <Button
              type="button"
              variant="secondary"
              disabled={!checked || run.isPending || checked.counts.added === 0}
              onClick={() => send(false)}
            >
              {run.isPending && run.variables && !run.variables.dryRun ? "Importing…" : "Import"}
            </Button>
          </div>
          <p className="text-sm text-muted-foreground">
            Check the file first. It shows what each row would do and writes nothing. Import is available once the file has been
            checked.
          </p>
        </CardContent>
      </Card>

      <div role="status" aria-live="polite" className="sr-only">
        {run.isPending ? (run.variables?.dryRun ? "Checking the file" : "Importing") : ""}
        {shown ? summary(shown) : ""}
      </div>

      {run.isError && (
        <ErrorAlert error={run.error} title="The file was not processed" message={fileMessage(run.error)} />
      )}

      {shown && <Report report={shown} />}
    </section>
  );
}

function summary(report: UserImportReport): string {
  const c = report.counts;
  const parts = [
    `${c.rows} rows`,
    `${c.accountsCreated} new ${c.accountsCreated === 1 ? "person" : "people"}`,
    `${c.added} ${report.dryRun ? "to add" : "added"}`,
    `${c.alreadyPresent} already there`,
    `${c.errors} with errors`,
    `${c.skipped} skipped`,
  ];
  if (c.notProcessed > 0) parts.push(`${c.notProcessed} not processed`);
  return parts.join(", ");
}

function Report({ report }: { report: UserImportReport }) {
  const { counts, dryRun } = report;
  return (
    <Card>
      <CardHeader>
        <CardTitle>{dryRun ? "What the file would do" : "What the import did"}</CardTitle>
        <CardDescription data-testid="import-summary">{summary(report)}</CardDescription>
      </CardHeader>
      <CardContent className="grid gap-4">
        {counts.errors > 0 && (
          <p className="text-sm">
            Rows with an error are not applied, and none of the other rows of the same person are either. Correct the file and
            send it again; what was already done is left as it is.
          </p>
        )}
        {counts.notProcessed > 0 && (
          <p className="text-sm">Some rows were not reached. Send the same file again to finish them.</p>
        )}
        <Table scrollLabel="Import report">
          <TableCaption className="sr-only">The rows of the file and what happens to each</TableCaption>
          <TableHeader>
            <TableRow>
              <TableHead>Line</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Place</TableHead>
              <TableHead>Result</TableHead>
              <TableHead>Details</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {report.lines.map((line) => (
              <TableRow key={line.line} data-testid="import-row">
                <TableCell>{line.line}</TableCell>
                <TableCell className="break-all">{line.email}</TableCell>
                <TableCell>{line.role}</TableCell>
                <TableCell>{line.scope || "—"}</TableCell>
                <TableCell>
                  <Badge variant={line.outcome === "ERROR" ? "destructive" : "outline"}>
                    {outcomeText(line.outcome, dryRun)}
                  </Badge>
                  {line.accountCreated && line.outcome === "ADDED" && (
                    <span className="ml-2 text-xs text-muted-foreground">{dryRun ? "new person" : "invited"}</span>
                  )}
                </TableCell>
                <TableCell className="text-sm text-muted-foreground">{line.message ?? ""}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </CardContent>
    </Card>
  );
}
