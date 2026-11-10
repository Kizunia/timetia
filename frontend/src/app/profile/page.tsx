import { PageShell } from "@/components/PageShell";
import { ProfileScreen } from "@/features/identity/ProfileScreen";
import { config } from "@/lib/config";

export const dynamic = "force-dynamic";

export default function ProfilePage() {
  // Keycloak's account console is where a user changes their name, email address and password (ADR 0041).
  return (
    <PageShell>
      <ProfileScreen accountUrl={`${config().issuer}/account`} />
    </PageShell>
  );
}
