"use client";

import { useState } from "react";
import { toast } from "sonner";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Textarea } from "@/components/ui/textarea";
import { useBatches, useDivisions, useFaculties } from "@/features/campus/api";
import { useMyPlaces } from "@/features/campus/useMyPlaces";
import { useCreateIssue } from "@/features/issues/api";
import { SESSION_LABEL } from "@/lib/labels";

type Session = keyof typeof SESSION_LABEL;

/**
 * Opens an issue for the class the person represents: a class representative for a theory issue (their division), a batch
 * representative for a lab or tutorial issue (their batch). The server checks the place; this offers only what they hold.
 */
export function IssueForm({ onDone }: { onDone: () => void }) {
  const places = useMyPlaces();
  const divisions = useDivisions();
  const batches = useBatches();
  const faculties = useFaculties();
  const create = useCreateIssue();
  const [session, setSession] = useState<Session>("THEORY");
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [mentioned, setMentioned] = useState<string[]>([]);
  const [problem, setProblem] = useState<string | null>(null);
  const [placeId, setPlaceId] = useState("");

  const theory = session === "THEORY";
  const placeIds = theory ? places.divisionIds : places.batchIds;
  const chosenPlace = placeIds.includes(placeId) ? placeId : (placeIds[0] ?? "");
  const options = theory ? (divisions.data ?? []) : (batches.data ?? []);

  const submit = async () => {
    if (!title.trim()) return setProblem("Enter a title");
    if (!chosenPlace) return setProblem(theory ? "You are not the class representative of a division" : "You are not the representative of a batch");
    setProblem(null);
    try {
      await create.mutateAsync({
        sessionType: session,
        title: title.trim(),
        description: description.trim() || undefined,
        ...(theory ? { divisionId: chosenPlace } : { batchId: chosenPlace }),
        mentionedFacultyIds: mentioned,
      });
      toast.success("Issue opened");
      onDone();
    } catch {
      // The alert below shows what the server said.
    }
  };

  return (
    <div className="grid gap-4 rounded-lg border p-4">
      <div className="grid gap-4 sm:grid-cols-2">
        <div className="grid gap-2">
          <Label htmlFor="issue-session">About</Label>
          <Select value={session} onValueChange={(v) => setSession(v as Session)}>
            <SelectTrigger id="issue-session">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {(Object.keys(SESSION_LABEL) as Session[]).map((s) => (
                <SelectItem key={s} value={s}>
                  {SESSION_LABEL[s]}
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
        <div className="grid gap-2">
          <Label htmlFor="issue-place">{theory ? "Division" : "Batch"}</Label>
          <Select value={chosenPlace} onValueChange={setPlaceId}>
            <SelectTrigger id="issue-place">
              <SelectValue placeholder="Choose" />
            </SelectTrigger>
            <SelectContent>
              {options
                .filter((o) => placeIds.includes(o.id))
                .map((o) => (
                  <SelectItem key={o.id} value={o.id}>
                    {o.code}
                  </SelectItem>
                ))}
            </SelectContent>
          </Select>
        </div>
      </div>
      <div className="grid gap-2">
        <Label htmlFor="issue-title">Title</Label>
        <Input id="issue-title" value={title} maxLength={200} onChange={(e) => setTitle(e.target.value)} />
      </div>
      <div className="grid gap-2">
        <Label htmlFor="issue-description">What does the class want? (optional)</Label>
        <Textarea id="issue-description" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} />
      </div>
      <fieldset className="grid gap-2">
        <legend className="mb-1 text-sm font-medium">Teachers it concerns</legend>
        <div className="flex flex-wrap gap-x-6 gap-y-2">
          {(faculties.data ?? []).map((f) => (
            <div key={f.id} className="flex items-center gap-2">
              <Checkbox
                id={`m-${f.id}`}
                checked={mentioned.includes(f.id)}
                onCheckedChange={(on) => setMentioned(on === true ? [...mentioned, f.id] : mentioned.filter((x) => x !== f.id))}
              />
              <Label htmlFor={`m-${f.id}`}>{f.name}</Label>
            </div>
          ))}
        </div>
      </fieldset>
      {problem && (
        <p role="alert" className="text-sm text-destructive">
          {problem}
        </p>
      )}
      {create.isError && <ErrorAlert error={create.error} title="The issue was not opened" />}
      <div className="flex gap-2">
        <Button onClick={() => void submit()} disabled={create.isPending}>
          {create.isPending ? "Opening…" : "Open issue"}
        </Button>
        <Button variant="outline" onClick={onDone}>
          Cancel
        </Button>
      </div>
    </div>
  );
}
