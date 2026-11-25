import { PageShell } from "@/components/PageShell";
import { IssuesScreen } from "@/features/issues/IssuesScreen";

export const dynamic = "force-dynamic";

export default function IssuesPage() {
  return (
    <PageShell width="wide">
      <IssuesScreen />
    </PageShell>
  );
}
