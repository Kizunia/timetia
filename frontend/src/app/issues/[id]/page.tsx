import { PageShell } from "@/components/PageShell";
import { IssueDetailScreen } from "@/features/issues/IssueDetailScreen";

export const dynamic = "force-dynamic";

export default async function IssuePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <PageShell>
      <IssueDetailScreen id={id} />
    </PageShell>
  );
}
