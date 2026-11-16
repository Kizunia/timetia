"use client";

import { BellIcon } from "lucide-react";
import Link from "next/link";

import { useUnreadCount } from "@/features/notifications/api";

/** The bell in the header: a link to the notifications with the number of unread ones. */
export function NotificationBell() {
  const unread = useUnreadCount();
  const count = unread.data ?? 0;
  return (
    <Link
      href="/notifications"
      className="relative inline-flex items-center rounded-md p-2 text-muted-foreground hover:bg-muted hover:text-foreground"
      aria-label={count > 0 ? `Notifications, ${count} unread` : "Notifications"}
    >
      <BellIcon aria-hidden="true" className="size-5" />
      {count > 0 && (
        <span data-testid="unread-count" className="absolute -right-0.5 -top-0.5 min-w-5 rounded-full bg-destructive px-1 text-center text-xs font-semibold text-white">
          {count > 99 ? "99+" : count}
        </span>
      )}
    </Link>
  );
}
