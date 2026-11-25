"use client";

import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { useCreateTicket } from "@/features/tickets/api";
import { TicketForm } from "@/features/tickets/components/TicketForm";
import { buildCreateBody, type TicketFormValues, type TicketKind } from "@/features/tickets/lib/request";
import { lectureHours, slotDefaults } from "@/features/tickets/lib/slot";
import type { Lecture } from "@/features/timetable/api";

export const KIND_TITLE: Record<TicketKind, string> = {
  RESCHEDULE: "Reschedule this lecture",
  CANCEL: "Cancel this lecture",
  SUBSTITUTE: "Find a substitute",
  EXCHANGE: "Exchange with another lecture",
  PERMANENT_CHANGE: "Change this lecture for good",
  EXTRA: "New extra lecture",
};

/** The values a form starts with: where the lecture is now, so a change is a matter of editing what is already there. */
export function defaultsOf(lecture: Lecture): TicketFormValues {
  return { ...slotDefaults(lecture), description: "", reason: "" };
}

/**
 * Raises a ticket of one kind for a lecture. A ticket with conflicts is still created (the answer is a success); the
 * conflicts are shown on its page, which is where the person goes next.
 */
export function RaiseDialog({
  kind,
  lecture,
  open,
  onOpenChange,
}: {
  kind: Exclude<TicketKind, "EXTRA">;
  lecture: Lecture;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const router = useRouter();
  const create = useCreateTicket();
  const moving = kind === "RESCHEDULE" || kind === "PERMANENT_CHANGE";

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="max-h-[90vh] overflow-y-auto sm:max-w-xl">
        <DialogHeader>
          <DialogTitle>{KIND_TITLE[kind]}</DialogTitle>
          <DialogDescription>
            {lecture.subjectCode ?? "Lecture"}, {lecture.divisionCode}
            {lecture.batchCode ? ` · ${lecture.batchCode}` : ""}. The ticket is checked for conflicts when you create it.
          </DialogDescription>
        </DialogHeader>
        <TicketForm
          kind={kind}
          context={{
            sessionType: lecture.sessionType,
            lockedHours: moving ? lectureHours(lecture) : undefined,
            excludeLecture: lecture.id,
            teacherFacultyId: lecture.facultyId,
            lecture,
          }}
          defaults={defaultsOf(lecture)}
          submitLabel="Create ticket"
          onCancel={() => onOpenChange(false)}
          onSubmit={async (values) => {
            const created = await create.mutateAsync(buildCreateBody(kind, lecture.id, values));
            toast.success("Ticket created");
            onOpenChange(false);
            router.push(`/tickets/${created.id}`);
          }}
        />
      </DialogContent>
    </Dialog>
  );
}
