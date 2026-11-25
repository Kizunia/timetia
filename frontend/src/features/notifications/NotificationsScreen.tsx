"use client";

import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Button } from "@/components/ui/button";
import { Skeleton } from "@/components/ui/skeleton";
import { Tabs, TabsContent, TabsList, TabsTrigger } from "@/components/ui/tabs";
import { useMarkAllRead, useMarkRead, useNotifications, useUnreadCount } from "@/features/notifications/api";
import { NotificationRow } from "@/features/notifications/components/NotificationRow";

const TABS = [
  { value: "all", label: "All" },
  { value: "unread", label: "Unread" },
] as const;

type Tab = (typeof TABS)[number]["value"];

/** What happened that concerns the person, newest first. Opening one marks it read. */
export function NotificationsScreen() {
  const [page, setPage] = useState(0);
  const [tab, setTab] = useState<Tab>("all");
  const list = useNotifications({ page, size: 20, ...(tab === "unread" ? { unread: true } : {}) });
  const unread = useUnreadCount();
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  const failure = markAll.error ?? markRead.error;

  return (
    <section aria-labelledby="notifications-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="notifications-heading" className="text-2xl font-semibold tracking-tight">
          Notifications
        </h1>
        <Button variant="outline" onClick={() => markAll.mutate()} disabled={markAll.isPending || (unread.data ?? 0) === 0}>
          Mark all read
        </Button>
      </div>

      {failure && <ErrorAlert error={failure} title="It was not marked as read" />}

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
            {list.isError && <ErrorAlert error={list.error} title="The notifications could not be loaded" />}
            {list.isPending && <Skeleton className="h-24 w-full" aria-label="Loading notifications" />}
            {list.data && (
              <>
                <ul className="flex flex-col gap-2">
                  {list.data.items.map((notification) => (
                    <NotificationRow key={notification.id} notification={notification} onRead={() => markRead.mutate(notification.id)} />
                  ))}
                  {list.data.items.length === 0 && <li className="text-muted-foreground">Nothing to show</li>}
                </ul>
                <Pager
                  page={list.data.page.number}
                  totalPages={list.data.page.totalPages}
                  totalElements={list.data.page.totalElements}
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
