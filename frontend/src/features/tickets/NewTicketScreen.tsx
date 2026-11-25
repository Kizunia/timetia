"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useMyCampus } from "@/features/campus/api";
import { useCreateTicket } from "@/features/tickets/api";
import { TicketForm } from "@/features/tickets/components/TicketForm";
import { buildCreateBody } from "@/features/tickets/lib/request";
import { addDays, todayIso } from "@/lib/dates";

/** Raises an extra lecture: a lecture of one of the subjects the person teaches that is not in the weekly timetable. */
export function NewTicketScreen() {
  const router = useRouter();
  const campus = useMyCampus();
  const create = useCreateTicket();

  return (
    <section aria-labelledby="new-ticket-heading" className="grid gap-6">
      <p>
        <Link href="/tickets" className="text-sm font-medium text-primary underline underline-offset-2">
          ← All tickets
        </Link>
      </p>
      <h1 id="new-ticket-heading" className="text-2xl font-semibold tracking-tight">
        New extra lecture
      </h1>
      {campus.isPending && <Skeleton className="h-48 w-full" aria-label="Loading your subjects" />}
      {campus.isError && <ErrorAlert error={campus.error} title="Your subjects could not be loaded" />}
      {campus.data && campus.data.assignments.length === 0 && (
        <p className="text-muted-foreground">You do not teach any subject yet.</p>
      )}
      {campus.data && campus.data.assignments.length > 0 && (
        <Card>
          <CardHeader>
            <CardTitle>Which lecture, and when</CardTitle>
          </CardHeader>
          <CardContent>
            <TicketForm
              kind="EXTRA"
              context={{}}
              defaults={{ date: addDays(todayIso(), 1), startHour: 9, endHour: 10, mode: "IN_PERSON", description: "" }}
              submitLabel="Create ticket"
              onCancel={() => router.push("/tickets")}
              onSubmit={async (values) => {
                const created = await create.mutateAsync(buildCreateBody("EXTRA", null, values));
                toast.success("Ticket created");
                router.push(`/tickets/${created.id}`);
              }}
            />
          </CardContent>
        </Card>
      )}
    </section>
  );
}
