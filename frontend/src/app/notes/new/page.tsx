import { PageShell } from "@/components/PageShell";
import { NoteEditor } from "@/features/notes/NoteEditor";

export const dynamic = "force-dynamic";

export default function NewNotePage() {
  return (
    <PageShell>
      <NoteEditor />
    </PageShell>
  );
}
