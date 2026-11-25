"use client";

import Link from "next/link";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Button } from "@/components/ui/button";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Can, usePermissions } from "@/features/identity/permissions";
import { useTickets, type Ticket, type TicketListParams } from "@/features/tickets/api";
import { TicketTable } from "@/features/tickets/components/TicketTable";
import { useTicketsUi, type TicketsTab } from "@/features/tickets/store";
import { TICKET_STATUS_LABEL } from "@/lib/labels";

const ALL = "all";
const STATUSES = Object.keys(TICKET_STATUS_LABEL) as Ticket["status"][];

const TABS: { value: TicketsTab; label: string; empty: string }[] = [
  { value: "mine", label: "Mine", empty: "You have not raised any tickets" },
  { value: "review", label: "To review", empty: "Nothing is waiting for your review" },
  { value: "all", label: "All I can see", empty: "No tickets yet" },
];

/** What each tab asks the API for. */
const QUERY: Record<TicketsTab, Pick<TicketListParams, "mine" | "awaitingReview">> = {
  mine: { mine: true },
  review: { awaitingReview: true },
  all: {},
};

/** The tickets the person may see, in three views: the ones they raised, the ones waiting for their answer, and all of them. */
export function TicketsScreen() {
  const { has } = usePermissions();
  const chosen = useTicketsUi((s) => s.tab);
  const status = useTicketsUi((s) => s.status);
  const page = useTicketsUi((s) => s.page);
  const { setTab, setStatus, setPage } = useTicketsUi.getState();
  const tab = chosen ?? (has("rescheduling.tickets.create") ? "mine" : "all");

  const tickets = useTickets({
    page,
    size: 20,
    sort: "createdAt,desc",
    ...(status ? { status } : {}),
    ...QUERY[tab],
  });

  return (
    <section aria-labelledby="tickets-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="tickets-heading" className="text-2xl font-semibold tracking-tight">
          Tickets
        </h1>
        <Can permission="rescheduling.tickets.create">
          <Button asChild>
            <Link href="/tickets/new">New extra lecture</Link>
          </Button>
        </Can>
      </div>

      <Tabs value={tab} onValueChange={(value) => setTab(value as TicketsTab)}>
        <div className="flex flex-wrap items-end justify-between gap-3">
          <TabsList>
            {TABS.map((option) => (
              <TabsTrigger key={option.value} value={option.value}>
                {option.label}
              </TabsTrigger>
            ))}
          </TabsList>
          <div className="flex flex-col gap-1">
            <Label htmlFor="ticket-status">Status</Label>
            <Select value={status ?? ALL} onValueChange={(value) => setStatus(value === ALL ? null : (value as Ticket["status"]))}>
              <SelectTrigger id="ticket-status" className="w-44">
                <SelectValue />
              </SelectTrigger>
              <SelectContent>
                <SelectItem value={ALL}>All statuses</SelectItem>
                {STATUSES.map((value) => (
                  <SelectItem key={value} value={value}>
                    {TICKET_STATUS_LABEL[value]}
                  </SelectItem>
                ))}
              </SelectContent>
            </Select>
          </div>
        </div>

        {TABS.map((option) => (
          <TabsContent key={option.value} value={option.value} className="mt-4">
            {tickets.isError && <ErrorAlert error={tickets.error} title="The tickets could not be loaded" />}
            {tickets.isPending && (
              <div className="grid gap-2" aria-busy="true" aria-label="Loading tickets">
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
                <Skeleton className="h-9 w-full" />
              </div>
            )}
            {tickets.data && (
              <>
                <TicketTable tickets={tickets.data.items} empty={option.empty} />
                <Pager
                  page={tickets.data.page.number}
                  totalPages={tickets.data.page.totalPages}
                  totalElements={tickets.data.page.totalElements}
                  onPage={setPage}
                />
              </>
            )}
          </TabsContent>
        ))}
      </Tabs>
    </section>
  );
}
