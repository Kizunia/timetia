"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";

import { ErrorAlert } from "@/components/ErrorAlert";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Skeleton } from "@/components/ui/skeleton";
import { Textarea } from "@/components/ui/textarea";
import { usePermissions } from "@/features/identity/permissions";
import { useCreateNote, useDeleteNote, useNote, useUpdateNote, type Note } from "@/features/notes/api";
import { NoteAttachment } from "@/features/notes/NoteAttachment";
import { applyProblem } from "@/lib/forms";

/** The form's own checks are for usability; the server's validation is authoritative and is shown too. */
const schema = z.object({
  title: z.string().trim().min(1, "Enter a title").max(120, "Use at most 120 characters"),
  body: z.string().max(4000, "Use at most 4000 characters"),
});

type Values = z.input<typeof schema>;

/** A note to write (no ID) or to read and change. */
export function NoteEditor({ noteId }: { noteId?: string }) {
  const note = useNote(noteId);

  return (
    <section aria-labelledby="note-heading" className="grid gap-6">
      <p>
        <Link href="/notes" className="text-sm font-medium text-primary underline underline-offset-2">
          ← All notes
        </Link>
      </p>
      {noteId && note.isError && <ErrorAlert error={note.error} title="The note could not be opened" />}
      {noteId && note.isPending && <Skeleton className="h-48 w-full" aria-label="Loading the note" />}
      {(!noteId || note.data) && (
        // Keyed by the version, so the form starts again from the server's note after a save. Attaching a picture does not
        // change the version, so it does not throw away what is being typed.
        <NoteForm key={note.data ? `${note.data.id}-${note.data.version}` : "new"} note={note.data} />
      )}
    </section>
  );
}

function NoteForm({ note }: { note?: Note }) {
  const router = useRouter();
  const { me, has } = usePermissions();
  const create = useCreateNote();
  const update = useUpdateNote(note?.id ?? "");
  const remove = useDeleteNote(note?.id ?? "");
  const [failure, setFailure] = useState<string | null>(null);

  // Whose note it is decides which permission counts: the plain one is for your own, the "-any" one for anyone's. This only
  // decides what is shown; the API enforces it.
  const own = !note || note.ownerId === me.id;
  const canChange = note ? has(own ? "notes.notes.update" : "notes.notes.update-any") : has("notes.notes.create");
  const canDelete = note ? has(own ? "notes.notes.delete" : "notes.notes.delete-any") : false;

  const form = useForm<Values, unknown, z.output<typeof schema>>({
    resolver: zodResolver(schema),
    defaultValues: { title: note?.title ?? "", body: note?.body ?? "" },
  });

  const mutation = note ? update : create;
  const submit = form.handleSubmit(async (values) => {
    setFailure(null);
    try {
      // Awaited, not given callbacks: saving refreshes the note, which starts this form again with the new version, and a
      // callback given to `mutate` is dropped for a form that has gone by then.
      const saved = note ? await update.mutateAsync({ ...values, version: note.version }) : await create.mutateAsync(values);
      toast.success(note ? "Note saved" : "Note created");
      if (!note) router.push(`/notes/${saved.id}`);
    } catch (error) {
      setFailure(
        applyProblem(error, ["title", "body"], (field, message) => form.setError(field as "title", { message }), {
          "notes.duplicate_title": "title",
        }),
      );
    }
  });

  return (
    <>
      <form onSubmit={submit} noValidate className="grid gap-6">
        <div className="flex flex-wrap items-center gap-3">
          <h1 id="note-heading" className="text-2xl font-semibold tracking-tight">
            {note ? note.title : "New note"}
          </h1>
          {note && !own && <span className="text-sm text-muted-foreground">Written by someone else</span>}
        </div>

        <Card>
          <CardHeader>
            <CardTitle>Note</CardTitle>
          </CardHeader>
          <CardContent className="grid gap-4">
            <div className="grid gap-2">
              <Label htmlFor="note-title">Title</Label>
              <Input
                id="note-title"
                readOnly={!canChange}
                aria-invalid={form.formState.errors.title ? true : undefined}
                aria-describedby={form.formState.errors.title ? "note-title-error" : undefined}
                {...form.register("title")}
              />
              {form.formState.errors.title && (
                <p id="note-title-error" role="alert" className="text-sm text-destructive">
                  {form.formState.errors.title.message}
                </p>
              )}
            </div>
            <div className="grid gap-2">
              <Label htmlFor="note-body">Text</Label>
              <Textarea
                id="note-body"
                rows={10}
                readOnly={!canChange}
                aria-invalid={form.formState.errors.body ? true : undefined}
                aria-describedby={form.formState.errors.body ? "note-body-error" : undefined}
                {...form.register("body")}
              />
              {form.formState.errors.body && (
                <p id="note-body-error" role="alert" className="text-sm text-destructive">
                  {form.formState.errors.body.message}
                </p>
              )}
            </div>
            {note && (
              <p className="text-sm text-muted-foreground">
                Created {new Date(note.createdAt).toLocaleString()}. Last changed {new Date(note.updatedAt).toLocaleString()}.
              </p>
            )}
            {note && !canChange && <p className="text-sm text-muted-foreground">You can read this note but not change it.</p>}
          </CardContent>
        </Card>

        {failure && <ErrorAlert error={mutation.error} title="The note was not saved" />}
        {remove.isError && <ErrorAlert error={remove.error} title="The note was not deleted" />}

        <div className="flex flex-wrap items-center gap-3">
          {canChange && (
            <Button type="submit" disabled={mutation.isPending}>
              {mutation.isPending ? "Saving…" : note ? "Save note" : "Create note"}
            </Button>
          )}
          {note && canDelete && (
            <AlertDialog>
              <AlertDialogTrigger asChild>
                <Button type="button" variant="destructive" disabled={remove.isPending}>
                  Delete note
                </Button>
              </AlertDialogTrigger>
              <AlertDialogContent>
                <AlertDialogHeader>
                  <AlertDialogTitle>Delete this note?</AlertDialogTitle>
                  <AlertDialogDescription>
                    It disappears from the lists at once. Someone who may restore notes can bring it back, with its picture,
                    until it is removed for good after the retention period.
                  </AlertDialogDescription>
                </AlertDialogHeader>
                <AlertDialogFooter>
                  <AlertDialogCancel>Cancel</AlertDialogCancel>
                  <AlertDialogAction
                    onClick={async () => {
                      try {
                        await remove.mutateAsync();
                        toast.success("Note deleted");
                        router.push("/notes");
                      } catch {
                        // The alert above shows what went wrong.
                      }
                    }}
                  >
                    Delete
                  </AlertDialogAction>
                </AlertDialogFooter>
              </AlertDialogContent>
            </AlertDialog>
          )}
        </div>
      </form>

      {note && <NoteAttachment note={note} canChange={canChange} />}
    </>
  );
}
