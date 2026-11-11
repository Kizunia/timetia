import { PageShell } from "@/components/PageShell";
import { NotesScreen } from "@/features/notes/NotesScreen";

export const dynamic = "force-dynamic";

export default function NotesPage() {
  return (
    <PageShell width="wide">
      <NotesScreen />
    </PageShell>
  );
}
