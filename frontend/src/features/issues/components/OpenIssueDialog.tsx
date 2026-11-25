"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useBatches, useDivisions, useFaculties } from "@/features/campus/api";
import { useMyPlaces } from "@/features/campus/useMyPlaces";
import { useOpenIssue } from "@/features/issues/api";
import { FacultyPicker } from "@/features/issues/components/FacultyPicker";
import { issueBody, issueSchema, type IssueValues } from "@/features/issues/schemas";
import { applyProblem } from "@/lib/forms";
import { SESSION_LABEL } from "@/lib/labels";

const FIELDS = ["sessionType", "divisionId", "batchId", "title", "description", "mentionedFacultyIds"] as const;

function FieldError({ message }: { message?: string }) {
  return message ? (
    <p role="alert" className="text-sm text-destructive">
      {message}
    </p>
  ) : null;
}

/**
 * Opens an issue for the class the person represents: a theory issue for a division, a lab or tutorial issue for a batch.
 * The places offered are those the person holds a role in, when they hold any; the server decides what is allowed.
 */
export function OpenIssueDialog({ onClose }: { onClose: () => void }) {
  const router = useRouter();
  const places = useMyPlaces();
  const divisions = useDivisions();
  const batches = useBatches();
  const faculties = useFaculties();
  const open = useOpenIssue();
  const [failure, setFailure] = useState<string | null>(null);

  const form = useForm<IssueValues>({
    resolver: zodResolver(issueSchema),
    defaultValues: { sessionType: "THEORY", title: "", description: "", mentionedFacultyIds: [] },
  });
  const { errors, isSubmitting } = form.formState;
  const [sessionType, divisionId, batchId, mentioned] = useWatch({
    control: form.control,
    name: ["sessionType", "divisionId", "batchId", "mentionedFacultyIds"],
  });

  const theory = sessionType === "THEORY";
  const mine = theory ? places.divisionIds : places.batchIds;
  const all: { id: string; code: string }[] = (theory ? divisions.data : batches.data) ?? [];
  const options = mine.length > 0 ? all.filter((place) => mine.includes(place.id)) : all;

  const division = theory ? divisionId : batches.data?.find((batch) => batch.id === batchId)?.divisionId;
  const departmentId = divisions.data?.find((d) => d.id === division)?.departmentId;
  const teachers = (faculties.data ?? []).filter((f) => !departmentId || f.departmentId === departmentId);

  const submit = form.handleSubmit(async (values) => {
    setFailure(null);
    try {
      const created = await open.mutateAsync(issueBody(values));
      toast.success("Issue opened");
      onClose();
      router.push(`/issues/${created.id}`);
    } catch (error) {
      setFailure(applyProblem(error, FIELDS, (field, message) => form.setError(field as (typeof FIELDS)[number], { message })));
    }
  });

  const choosePlace = (id: string) => {
    form.setValue(theory ? "divisionId" : "batchId", id, { shouldValidate: form.formState.isSubmitted });
    // The teachers offered depend on the place, so an earlier choice may no longer be among them.
    form.setValue("mentionedFacultyIds", []);
  };

  return (
    <Dialog open onOpenChange={(next) => !next && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-lg">
        <DialogHeader>
          <DialogTitle>Open an issue</DialogTitle>
          <DialogDescription>Say what the class wants. The teachers you mention are told.</DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="grid gap-4">
          <div className="grid gap-4 sm:grid-cols-2">
            <div className="grid gap-2">
              <Label htmlFor="issue-session">About</Label>
              <Select
                value={sessionType}
                onValueChange={(value) => {
                  form.setValue("sessionType", value as IssueValues["sessionType"]);
                  form.setValue("mentionedFacultyIds", []);
                  form.clearErrors(["divisionId", "batchId"]);
                }}
              >
                <SelectTrigger id="issue-session">
                  <SelectValue />
                </SelectTrigger>
                <SelectContent>
                  {Object.entries(SESSION_LABEL).map(([value, label]) => (
                    <SelectItem key={value} value={value}>
                      {label}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
            </div>
            <div className="grid gap-2">
              <Label htmlFor="issue-place">{theory ? "Division" : "Batch"}</Label>
              <Select value={(theory ? divisionId : batchId) ?? ""} onValueChange={choosePlace}>
                <SelectTrigger id="issue-place">
                  <SelectValue placeholder="Choose" />
                </SelectTrigger>
                <SelectContent>
                  {options.map((place) => (
                    <SelectItem key={place.id} value={place.id}>
                      {place.code}
                    </SelectItem>
                  ))}
                </SelectContent>
              </Select>
              <FieldError message={(theory ? errors.divisionId : errors.batchId)?.message} />
            </div>
          </div>
          <div className="grid gap-2">
            <Label htmlFor="issue-title">Title</Label>
            <Input id="issue-title" aria-invalid={Boolean(errors.title)} {...form.register("title")} />
            <FieldError message={errors.title?.message} />
          </div>
          <div className="grid gap-2">
            <Label htmlFor="issue-description">What does the class want? (optional)</Label>
            <Textarea id="issue-description" rows={3} aria-invalid={Boolean(errors.description)} {...form.register("description")} />
            <FieldError message={errors.description?.message} />
          </div>
          <FacultyPicker
            faculties={teachers}
            chosen={mentioned}
            onChange={(ids) => form.setValue("mentionedFacultyIds", ids)}
            error={errors.mentionedFacultyIds?.message}
          />
          {failure && <ErrorAlert error={open.error} title="The issue was not opened" />}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={isSubmitting}>
              {isSubmitting ? "Opening…" : "Open issue"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
