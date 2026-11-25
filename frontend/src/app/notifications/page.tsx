import { PageShell } from "@/components/PageShell";
import { NotificationsScreen } from "@/features/notifications/NotificationsScreen";

export const dynamic = "force-dynamic";

export default function NotificationsPage() {
  return (
    <PageShell>
      <NotificationsScreen />
    </PageShell>
  );
}
