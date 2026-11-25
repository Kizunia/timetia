"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm, useWatch } from "react-hook-form";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { useCreateRow } from "@/features/setup/api";
import { FieldSelect } from "@/features/setup/components/FieldSelect";
import type { Lookups } from "@/features/setup/lookups";
import type { Resource } from "@/features/setup/resources";
import { bodyOf, schemaFor, type FormValues } from "@/features/setup/schemas";
import { applyProblem } from "@/lib/forms";

export function CreateDialog({ resource, lookups, onClose }: { resource: Resource; lookups: Lookups; onClose: () => void }) {
  const create = useCreateRow(resource.key);
  const [failure, setFailure] = useState<string | null>(null);
  const fields = resource.createFields;
  const form = useForm<FormValues>({
    resolver: zodResolver(schemaFor(fields)),
    defaultValues: Object.fromEntries(fields.map((field) => [field.name, ""])),
  });
  const values = useWatch({ control: form.control }) as FormValues;

  const submit = form.handleSubmit(async (input) => {
    setFailure(null);
    try {
      await create.mutateAsync(bodyOf(fields, input));
      toast.success(`Created the ${resource.one}`);
      onClose();
    } catch (error) {
      setFailure(
        applyProblem(error, fields.map((field) => field.name), (field, message) => form.setError(field, { message }), {
          "reference.duplicate_code": "code",
        }),
      );
    }
  });

  return (
    <Dialog open onOpenChange={(open) => !open && onClose()}>
      <DialogContent className="max-h-[90vh] overflow-y-auto">
        <DialogHeader>
          <DialogTitle>New {resource.one}</DialogTitle>
          <DialogDescription>
            {resource.named ? "Only the name can be changed later." : "It cannot be changed later; delete it and make another."}
          </DialogDescription>
        </DialogHeader>
        <form onSubmit={submit} noValidate className="grid gap-4">
          {fields.map((field) => {
            const error = form.formState.errors[field.name]?.message;
            const id = `new-${field.name}`;
            if (field.name === "batchId" && (!values.sessionType || values.sessionType === "THEORY")) return null;
            if (field.type === "select") {
              let options = lookups.options(field.options!);
              // The batches offered are those of the chosen division.
              if (field.name === "batchId") options = options.filter((option) => option.row?.divisionId === values.divisionId);
              return (
                <FieldSelect
                  key={field.name}
                  id={id}
                  label={field.label}
                  value={values[field.name] ?? ""}
                  options={options}
                  onChange={(value) => {
                    form.setValue(field.name, value, { shouldValidate: form.formState.isSubmitted });
                    // A batch chosen earlier belongs to the old division.
                    if (field.name === "divisionId" && "batchId" in values) form.setValue("batchId", "");
                  }}
                  error={error}
                />
              );
            }
            return (
              <div key={field.name} className="grid gap-2">
                <Label htmlFor={id}>{field.label}</Label>
                <Input
                  id={id}
                  type={field.type === "date" ? "date" : "text"}
                  inputMode={field.type === "number" ? "numeric" : undefined}
                  aria-invalid={error ? true : undefined}
                  {...form.register(field.name)}
                />
                {error && (
                  <p role="alert" className="text-sm text-destructive">
                    {error}
                  </p>
                )}
              </div>
            );
          })}
          {failure && <ErrorAlert error={create.error} message={failure} title="It was not created" />}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={onClose}>
              Cancel
            </Button>
            <Button type="submit" disabled={form.formState.isSubmitting}>
              Create
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
