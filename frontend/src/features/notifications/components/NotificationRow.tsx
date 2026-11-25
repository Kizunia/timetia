import Link from "next/link";

import { Button } from "@/components/ui/button";
import type { Notification } from "@/features/notifications/api";
import { describeNotification } from "@/features/notifications/lib/describe";

/** One notification: its sentence, a link to what it is about when the person may open it, and the time. */
export function NotificationRow({ notification, onRead }: { notification: Notification; onRead: () => void }) {
  const { text, href } = describeNotification(notification);
  const unread = !notification.readAt;
  return (
    <li className="flex flex-wrap items-center justify-between gap-2 rounded-lg border p-3">
      <div className="flex items-start gap-2">
        {unread && <span role="img" aria-label="Unread" className="mt-2 size-2 shrink-0 rounded-full bg-primary" />}
        <div className="flex flex-col gap-1">
          <span className={unread ? "font-medium" : "text-muted-foreground"}>
            {href ? (
              <Link href={href} onClick={() => unread && onRead()} className="underline underline-offset-2">
                {text}
              </Link>
            ) : (
              text
            )}
          </span>
          <span className="text-xs text-muted-foreground">{new Date(notification.createdAt).toLocaleString()}</span>
        </div>
      </div>
      {unread && (
        <Button variant="ghost" size="sm" onClick={onRead}>
          Mark read
        </Button>
      )}
    </li>
  );
}
