"use client";

import { useRef, useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { usePermissions } from "@/features/identity/permissions";
import { attachmentUrl, useRemoveAttachment, useUploadAttachment, type Note } from "@/features/notes/api";
import { IMAGE_TYPES, checkImageFile } from "@/lib/images";

/**
 * The picture attached to a note (ADR 0029, ADR 0051). Nothing is shown where files are switched off, so a fork without
 * the files module needs no change here. Who may change the picture is who may change the note: `canChange` is for
 * usability only, and the API refuses what a hidden button would have done.
 *
 * <p>The picture is a plain `<img>`, as the avatar is, because it is private to the signed-in viewer: the browser must
 * fetch it with the session cookie, and it must never go through the image optimizer, which fetches without the cookie
 * and would keep a copy for everyone. The attachment version in the address is what refreshes a cached copy.
 *
 * <p>Saving awaits the call instead of passing callbacks, because the refresh that follows replaces this screen's data and
 * TanStack Query drops the callbacks of a mutation whose component has been remounted (ADR 0050).
 */
export function NoteAttachment({ note, canChange }: { note: Note; canChange: boolean }) {
  const { me } = usePermissions();
  const upload = useUploadAttachment(note.id);
  const remove = useRemoveAttachment(note.id);
  const input = useRef<HTMLInputElement>(null);
  const [refusal, setRefusal] = useState<string | null>(null);
  const busy = upload.isPending || remove.isPending;
  const version = note.attachmentVersion;

  if (!me.filesEnabled) return null;
  // Nothing to show and nothing the viewer may add: no empty card.
  if (!version && !canChange) return null;

  async function choose(file: File | undefined) {
    if (!file) return;
    const problem = checkImageFile(file);
    setRefusal(problem);
    remove.reset();
    if (problem) return;
    try {
      await upload.mutateAsync(file);
      toast.success("Picture saved");
    } catch {
      // The reason is shown below, from the mutation's error.
    }
  }

  async function removePicture() {
    setRefusal(null);
    upload.reset();
    try {
      await remove.mutateAsync();
      toast.success("Picture removed");
    } catch {
      // The reason is shown below, from the mutation's error.
    }
  }

  return (
    <Card>
      <CardHeader>
        <CardTitle>Picture</CardTitle>
        {canChange && (
          <CardDescription>
            JPEG, PNG or WebP, up to 5 MB. It is kept whole, only resized, and what the camera recorded in it, such as the
            location, is removed.
          </CardDescription>
        )}
      </CardHeader>
      <CardContent className="grid gap-4">
        {version ? (
          <a href={attachmentUrl(note.id, "full", version)} target="_blank" rel="noopener noreferrer" className="w-fit">
            {/* eslint-disable-next-line @next/next/no-img-element -- private image; see the note above */}
            <img
              src={attachmentUrl(note.id, "preview", version)}
              alt={`Picture attached to the note “${note.title}”`}
              className="max-h-80 w-auto max-w-full rounded-md border"
              data-testid="note-picture"
            />
            <span className="sr-only"> (opens the full size in a new tab)</span>
          </a>
        ) : (
          <p className="text-sm text-muted-foreground" data-testid="note-no-picture">
            This note has no picture.
          </p>
        )}
        {canChange && (
          <div className="flex flex-wrap gap-2">
            <Button type="button" disabled={busy} onClick={() => input.current?.click()}>
              {upload.isPending ? "Uploading…" : version ? "Replace picture" : "Add a picture"}
            </Button>
            {version && (
              <Button type="button" variant="outline" disabled={busy} onClick={() => void removePicture()}>
                Remove picture
              </Button>
            )}
            <input
              ref={input}
              type="file"
              accept={IMAGE_TYPES.join(",")}
              className="hidden"
              data-testid="attachment-input"
              aria-label="Picture file"
              tabIndex={-1}
              onChange={(event) => {
                const file = event.target.files?.[0];
                // Cleared so that choosing the same file again, after a failure, still counts as a choice.
                event.target.value = "";
                void choose(file);
              }}
            />
          </div>
        )}
        {refusal && (
          <p role="alert" className="text-sm text-destructive" data-testid="attachment-refusal">
            {refusal}
          </p>
        )}
        {upload.isError && <ErrorAlert error={upload.error} title="The picture was not saved" />}
        {remove.isError && <ErrorAlert error={remove.error} title="The picture was not removed" />}
      </CardContent>
    </Card>
  );
}
