import { PageShell } from "@/components/PageShell";
import { AdminFrame } from "@/features/identity/AdminFrame";

export const dynamic = "force-dynamic";

export default function AdminLayout({ children }: { children: React.ReactNode }) {
  return (
    <PageShell width="wide">
      <AdminFrame>{children}</AdminFrame>
    </PageShell>
  );
}
