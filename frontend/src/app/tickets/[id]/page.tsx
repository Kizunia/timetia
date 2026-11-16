import { PageShell } from "@/components/PageShell";
import { TicketDetailScreen } from "@/features/tickets/TicketDetailScreen";

export const dynamic = "force-dynamic";

export default async function TicketPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <PageShell width="wide">
      <TicketDetailScreen id={id} />
    </PageShell>
  );
}
