import { PageShell } from "@/components/PageShell";
import { TicketsScreen } from "@/features/tickets/TicketsScreen";

export const dynamic = "force-dynamic";

export default function TicketsPage() {
  return (
    <PageShell width="wide">
      <TicketsScreen />
    </PageShell>
  );
}
