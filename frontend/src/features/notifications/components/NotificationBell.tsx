"use client";

import { BellIcon } from "lucide-react";
import Link from "next/link";

import { Badge } from "@/components/ui/badge";
import { useUnreadCount } from "@/features/notifications/api";

/** The bell in the header: a link to the notifications with the number of unread ones. */
export function NotificationBell() {
  const unread = useUnreadCount();
  const count = unread.data ?? 0;
  return (
    <Link
      href="/notifications"
      className="inline-flex items-center gap-1 rounded-md p-2 text-muted-foreground hover:bg-muted hover:text-foreground focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-ring"
      aria-label={count > 0 ? `Notifications, ${count} unread` : "Notifications"}
    >
      <BellIcon aria-hidden="true" className="size-5" />
      {count > 0 && (
        <Badge variant="destructive" data-testid="unread-count">
          {count > 99 ? "99+" : count}
        </Badge>
      )}
    </Link>
  );
}
