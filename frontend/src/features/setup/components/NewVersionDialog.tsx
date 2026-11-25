"use client";

import { useRouter } from "next/navigation";
import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useCreateVersion, type Row } from "@/features/setup/api";
import { FieldSelect } from "@/features/setup/components/FieldSelect";
import { fieldErrors, ApiError } from "@/lib/api/problem";

/** Starts a draft: which division, in which term, from which date it takes effect. */
export function NewVersionDialog({ divisions, terms, onClose }: { divisions: Row[]; terms: Row[]; onClose: () => void }) {
  const router = useRouter();
  const create = useCreateVersion();
  const [divisionId, setDivisionId] = useState("");
  const [termId, setTermId] = useState("");
  const [effectiveFrom, setEffectiveFrom] = useState("");
  const problems = create.error instanceof ApiError ? fieldErrors(create.error.problem) : {};

  const save = async () => {
    try {
      const created = await create.mutateAsync({ divisionId, termId, effectiveFrom });
      toast.success("Draft created. Add its lectures, then publish it.");
      onClose();
      router.push(`/admin/timetables?version=${created.id}`);
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent>
        <DialogHeader>
          <DialogTitle>New timetable version</DialogTitle>
          <DialogDescription>A draft changes nothing until it is published.</DialogDescription>
        </DialogHeader>
        <FieldSelect
          id="new-version-division"
          label="Division"
          value={divisionId}
          options={divisions.map((d) => ({ value: d.id, label: d.code ?? "" }))}
          onChange={setDivisionId}
        />
        <FieldSelect
          id="new-version-term"
          label="Term"
          value={termId}
          options={terms.map((t) => ({ value: t.id, label: `${t.code} (${t.startDate} to ${t.endDate})` }))}
          onChange={setTermId}
        />
        <div className="grid gap-2">
          <Label htmlFor="new-version-from">Effective from</Label>
          <Input id="new-version-from" type="date" value={effectiveFrom} onChange={(event) => setEffectiveFrom(event.target.value)} />
          {problems.effectiveFrom && (
            <p role="alert" className="text-sm text-destructive">
              {problems.effectiveFrom.message}
            </p>
          )}
        </div>
        {create.isError && !problems.effectiveFrom && <ErrorAlert error={create.error} title="The draft was not created" />}
        <DialogFooter>
          <Button variant="outline" onClick={onClose}>
            Cancel
          </Button>
          <Button disabled={!divisionId || !termId || !effectiveFrom || create.isPending} onClick={() => void save()}>
            Create draft
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
