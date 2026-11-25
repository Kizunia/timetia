"use client";

import { toast } from "sonner";

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
import { usePublishVersion, type Version } from "@/features/setup/api";
import { TimetableError } from "@/features/setup/components/TimetableError";
import { formatDay } from "@/lib/dates";

/** Publishing makes the dated lectures of the term from the draft, and replaces the division's earlier ones. */
export function PublishButton({ version }: { version: Version }) {
  const publish = usePublishVersion(version.id);

  const confirm = async () => {
    try {
      const result = await publish.mutateAsync();
      toast.success(`Published: ${result.occurrencesCreated} lectures created, ${result.occurrencesSuperseded} replaced`);
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <div className="grid gap-3">
      {publish.isError && <TimetableError error={publish.error} title="The timetable was not published" />}
      <AlertDialog>
        <AlertDialogTrigger asChild>
          <Button className="justify-self-start" disabled={publish.isPending}>
            Publish
          </Button>
        </AlertDialogTrigger>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Publish this timetable?</AlertDialogTitle>
            <AlertDialogDescription>
              Lectures from {formatDay(version.effectiveFrom)} are created and replace the division&apos;s earlier ones.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Cancel</AlertDialogCancel>
            <AlertDialogAction onClick={() => void confirm()}>Publish timetable</AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </div>
  );
}
