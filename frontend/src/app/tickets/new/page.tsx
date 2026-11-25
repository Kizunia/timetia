import { PageShell } from "@/components/PageShell";
import { NewTicketScreen } from "@/features/tickets/NewTicketScreen";

export const dynamic = "force-dynamic";

export default function NewTicketPage() {
  return (
    <PageShell>
      <NewTicketScreen />
    </PageShell>
  );
}
