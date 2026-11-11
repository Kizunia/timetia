import { PageShell } from "@/components/PageShell";
import { NoteEditor } from "@/features/notes/NoteEditor";

export const dynamic = "force-dynamic";

export default async function NotePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return (
    <PageShell>
      <NoteEditor noteId={id} />
    </PageShell>
  );
}
