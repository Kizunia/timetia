"use client";

import Link from "next/link";
import { useState } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Pager } from "@/components/Pager";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { useMarkAllRead, useMarkRead, useNotifications } from "@/features/notifications/api";
import { describeNotification } from "@/features/notifications/describe";

/** What happened that concerns the person, newest first. Opening one marks it read. */
export function NotificationsScreen() {
  const [page, setPage] = useState(0);
  const [unreadOnly, setUnreadOnly] = useState(false);
  const list = useNotifications({ page, size: 20, ...(unreadOnly ? { unread: true } : {}) });
  const markRead = useMarkRead();
  const markAll = useMarkAllRead();
  return (
    <section aria-labelledby="notifications-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="notifications-heading" className="text-2xl font-semibold tracking-tight">Notifications</h1>
        <Button variant="outline" onClick={() => markAll.mutate()} disabled={markAll.isPending}>Mark all as read</Button>
      </div>
      <div className="flex items-center gap-2">
        <Checkbox id="only-unread" checked={unreadOnly} onCheckedChange={(on) => { setUnreadOnly(on === true); setPage(0); }} />
        <Label htmlFor="only-unread">Only unread</Label>
      </div>
      {list.isError && <ErrorAlert error={list.error} title="The notifications could not be loaded" />}
      {list.isPending && <Skeleton className="h-24 w-full" aria-label="Loading notifications" />}
      {list.data && (
        <>
          <ul className="flex flex-col gap-2">
            {list.data.items.map((n) => {
              const { text, href } = describeNotification(n);
              return (
                <li key={n.id} className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3">
                  <div className="flex flex-col gap-1">
                    <span className={n.readAt ? "text-muted-foreground" : "font-medium"}>
                      {href ? <Link href={href} onClick={() => !n.readAt && markRead.mutate(n.id)} className="underline underline-offset-2">{text}</Link> : text}
                    </span>
                    <span className="text-xs text-muted-foreground">{new Date(n.createdAt).toLocaleString()}</span>
                  </div>
                  {!n.readAt && <Badge>New</Badge>}
                </li>
              );
            })}
            {list.data.items.length === 0 && <li className="text-muted-foreground">Nothing here yet.</li>}
          </ul>
          <Pager page={list.data.page.number} totalPages={list.data.page.totalPages} totalElements={list.data.page.totalElements} onPage={setPage} />
        </>
      )}
    </section>
  );
}
