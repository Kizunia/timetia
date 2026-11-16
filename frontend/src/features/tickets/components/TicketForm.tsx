"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { Controller, FormProvider, useForm, type Resolver } from "react-hook-form";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useFaculties, useMyCampus } from "@/features/campus/api";
import { SlotFields } from "@/features/tickets/components/SlotFields";
import { exchangeCandidates, exchangeLabel } from "@/features/tickets/lib/exchange";
import { remap } from "@/features/tickets/lib/problems";
import type { TicketFormValues, TicketKind } from "@/features/tickets/lib/request";
import {
  cancelSchema,
  exchangeSchema,
  extraSchema,
  permanentSchema,
  rescheduleSchema,
  substituteSchema,
} from "@/features/tickets/schemas";
import { useLiveRange, type Lecture } from "@/features/timetable/api";
import { addDays, todayIso } from "@/lib/dates";
import { applyProblem } from "@/lib/forms";
import type { components } from "@/lib/api/schema.gen";
import { SESSION_LABEL } from "@/lib/labels";

type SessionType = components["schemas"]["LiveLectureResponse"]["sessionType"];

const SCHEMAS = {
  RESCHEDULE: rescheduleSchema,
  PERMANENT_CHANGE: permanentSchema,
  CANCEL: cancelSchema,
  SUBSTITUTE: substituteSchema,
  EXCHANGE: exchangeSchema,
  EXTRA: extraSchema,
} as const;

const FIELDS = [
  "date",
  "startHour",
  "endHour",
  "mode",
  "roomId",
  "meetingLink",
  "description",
  "reason",
  "substituteFacultyId",
  "otherLectureId",
  "assignmentId",
] as const;

/** What a form needs to know about the lecture it is for. */
export interface FormContext {
  sessionType?: SessionType;
  /** A move keeps the lecture's length, so the end hour follows the start. */
  lockedHours?: number;
  /** The lecture that is moving, whose own room counts as free for it. */
  excludeLecture?: string;
  /** The current teacher, who cannot be their own substitute. */
  teacherFacultyId?: string;
  /** The lecture to exchange, to find the ones it can be swapped with. */
  lecture?: Lecture;
}

function FieldError({ id, message }: { id: string; message?: string }) {
  return message ? (
    <p id={id} role="alert" className="text-sm text-destructive">
      {message}
    </p>
  ) : null;
}

/**
 * The fields of one kind of ticket, as a form. It checks what it can and sends the values to `onSubmit`; when the server
 * refuses, each message goes under its field and anything left is shown above the buttons. Raising a ticket and editing one
 * use the same form.
 */
export function TicketForm({
  kind,
  context,
  defaults,
  descriptionOnly = false,
  submitLabel,
  onSubmit,
  onCancel,
}: {
  kind: TicketKind;
  context: FormContext;
  defaults: TicketFormValues;
  /** An exchange being edited can change only its description. */
  descriptionOnly?: boolean;
  submitLabel: string;
  onSubmit: (values: TicketFormValues) => Promise<void>;
  onCancel: () => void;
}) {
  const schema = descriptionOnly ? exchangeSchema.pick({ description: true }) : SCHEMAS[kind];
  const form = useForm<TicketFormValues>({ resolver: zodResolver(schema) as unknown as Resolver<TicketFormValues>, defaultValues: defaults });
  const [failure, setFailure] = useState<{ message: string; error: unknown } | null>(null);
  const { errors, isSubmitting } = form.formState;

  const faculties = useFaculties();
  const campus = useMyCampus();
  const today = todayIso();
  const exchanging = kind === "EXCHANGE" && !descriptionOnly && context.lecture !== undefined;
  const others = useLiveRange(today, addDays(today, 60), { division: exchanging ? context.lecture!.divisionId : null }, exchanging);
  const assignmentId = form.watch("assignmentId");
  const sessionType = context.sessionType ?? campus.data?.assignments.find((a) => a.id === assignmentId)?.sessionType;

  const withSlot = !descriptionOnly && ["RESCHEDULE", "PERMANENT_CHANGE", "EXTRA"].includes(kind);

  const submit = form.handleSubmit(async (values) => {
    setFailure(null);
    try {
      await onSubmit(values);
    } catch (error) {
      const message = applyProblem(remap(error), FIELDS, (field, text) => form.setError(field as "date", { message: text }));
      if (message) setFailure({ message, error });
    }
  });

  return (
    <FormProvider {...form}>
      <form onSubmit={submit} noValidate className="grid gap-4">
        {kind === "PERMANENT_CHANGE" && !descriptionOnly && (
          <Alert>
            <AlertDescription>
              This moves this lecture and every later lecture of the same weekly entry by the same amount. An HOD must approve it.
            </AlertDescription>
          </Alert>
        )}

        {kind === "EXTRA" && !descriptionOnly && (
          <div className="grid gap-2">
            <Label htmlFor="ticket-assignment">Subject</Label>
            <Controller
              control={form.control}
              name="assignmentId"
              render={({ field }) => (
                <Select value={field.value ?? ""} onValueChange={field.onChange}>
                  <SelectTrigger id="ticket-assignment" aria-invalid={errors.assignmentId ? true : undefined}>
                    <SelectValue placeholder="Choose a subject" />
                  </SelectTrigger>
                  <SelectContent>
                    {(campus.data?.assignments ?? []).map((assignment) => (
                      <SelectItem key={assignment.id} value={assignment.id}>
                        {assignment.subjectCode} · {SESSION_LABEL[assignment.sessionType]} · {assignment.batchCode ?? assignment.divisionCode}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            <FieldError id="ticket-assignment-error" message={errors.assignmentId?.message} />
          </div>
        )}

        {withSlot && <SlotFields sessionType={sessionType} lockedHours={context.lockedHours} excludeLecture={context.excludeLecture} />}

        {kind === "SUBSTITUTE" && !descriptionOnly && (
          <div className="grid gap-2">
            <Label htmlFor="ticket-substitute">Who takes the lecture</Label>
            <Controller
              control={form.control}
              name="substituteFacultyId"
              render={({ field }) => (
                <Select value={field.value ?? ""} onValueChange={field.onChange}>
                  <SelectTrigger id="ticket-substitute" aria-invalid={errors.substituteFacultyId ? true : undefined}>
                    <SelectValue placeholder="Choose a teacher" />
                  </SelectTrigger>
                  <SelectContent>
                    {(faculties.data ?? [])
                      .filter((faculty) => faculty.id !== context.teacherFacultyId)
                      .map((faculty) => (
                        <SelectItem key={faculty.id} value={faculty.id}>
                          {faculty.name} ({faculty.code})
                        </SelectItem>
                      ))}
                  </SelectContent>
                </Select>
              )}
            />
            <FieldError id="ticket-substitute-error" message={errors.substituteFacultyId?.message} />
          </div>
        )}

        {exchanging && (
          <div className="grid gap-2">
            <Label htmlFor="ticket-exchange">Exchange with</Label>
            <Controller
              control={form.control}
              name="otherLectureId"
              render={({ field }) => (
                <Select value={field.value ?? ""} onValueChange={field.onChange} disabled={others.isPending}>
                  <SelectTrigger id="ticket-exchange" aria-invalid={errors.otherLectureId ? true : undefined}>
                    <SelectValue placeholder={others.isPending ? "Looking for lectures…" : "Choose a lecture"} />
                  </SelectTrigger>
                  <SelectContent>
                    {exchangeCandidates(others.data ?? [], context.lecture!).map((other) => (
                      <SelectItem key={other.id} value={other.id}>
                        {exchangeLabel(other)}
                      </SelectItem>
                    ))}
                  </SelectContent>
                </Select>
              )}
            />
            {others.isSuccess && exchangeCandidates(others.data, context.lecture!).length === 0 && (
              <p className="text-sm text-muted-foreground">No other teacher has a matching lecture in the next 60 days.</p>
            )}
            <FieldError id="ticket-exchange-error" message={errors.otherLectureId?.message} />
          </div>
        )}

        {(kind === "CANCEL" || kind === "SUBSTITUTE" || kind === "RESCHEDULE" || kind === "PERMANENT_CHANGE") && !descriptionOnly && (
          <div className="grid gap-2">
            <Label htmlFor="ticket-reason">{kind === "CANCEL" ? "Why is it cancelled?" : "Reason (optional)"}</Label>
            <Textarea id="ticket-reason" rows={2} aria-invalid={errors.reason ? true : undefined} {...form.register("reason")} />
            <FieldError id="ticket-reason-error" message={errors.reason?.message} />
          </div>
        )}

        <div className="grid gap-2">
          <Label htmlFor="ticket-description">Note (optional)</Label>
          <Textarea id="ticket-description" rows={2} {...form.register("description")} />
          <FieldError id="ticket-description-error" message={errors.description?.message} />
        </div>

        {failure && <ErrorAlert error={failure.error} message={failure.message} title="The ticket was not saved" />}

        <div className="flex flex-wrap justify-end gap-2">
          <Button type="button" variant="outline" onClick={onCancel}>
            Cancel
          </Button>
          <Button type="submit" disabled={isSubmitting}>
            {isSubmitting ? "Saving…" : submitLabel}
          </Button>
        </div>
      </form>
    </FormProvider>
  );
}
