"use client";

import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { Can } from "@/features/identity/permissions";
import { useIssues } from "@/features/issues/api";
import { IssueTable } from "@/features/issues/components/IssueTable";
import { OpenIssueDialog } from "@/features/issues/components/OpenIssueDialog";

const TABS = [
  { value: "open", label: "Open", empty: "There are no open issues", query: { status: "OPEN" } },
  { value: "closed", label: "Closed", empty: "There are no closed issues", query: { status: "CLOSED" } },
  { value: "mine", label: "Mine", empty: "You have not opened any issues", query: { mine: true } },
] as const;

type Tab = (typeof TABS)[number]["value"];

/** The issues the person may see: what classes ask for, which teachers they concern, and whether they are still open. */
export function IssuesScreen() {
  const [page, setPage] = useState(0);
  const [tab, setTab] = useState<Tab>("open");
  const [opening, setOpening] = useState(false);
  const issues = useIssues({ page, size: 20, ...TABS.find((option) => option.value === tab)!.query });

  return (
    <section aria-labelledby="issues-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="issues-heading" className="text-2xl font-semibold tracking-tight">
          Issues
        </h1>
        <Can permission="rescheduling.issues.open">
          <Button onClick={() => setOpening(true)}>Open an issue</Button>
        </Can>
      </div>
      {opening && <OpenIssueDialog onClose={() => setOpening(false)} />}

      <Tabs
        value={tab}
        onValueChange={(value) => {
          setTab(value as Tab);
          setPage(0);
        }}
      >
        <TabsList>
          {TABS.map((option) => (
            <TabsTrigger key={option.value} value={option.value}>
              {option.label}
            </TabsTrigger>
          ))}
        </TabsList>
        {TABS.map((option) => (
          <TabsContent key={option.value} value={option.value} className="mt-4 flex flex-col gap-4">
            {issues.isError && <ErrorAlert error={issues.error} title="The issues could not be loaded" />}
            {issues.isPending && <Skeleton className="h-24 w-full" aria-label="Loading issues" />}
            {issues.data && (
              <>
                <IssueTable issues={issues.data.items} empty={option.empty} />
                <Pager
                  page={issues.data.page.number}
                  totalPages={issues.data.page.totalPages}
                  totalElements={issues.data.page.totalElements}
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
