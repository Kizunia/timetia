"use client";

import { useState } from "react";

import { Checkbox } from "@/components/ui/checkbox";
import { Input } from "@/components/ui/input";
import { Label } from "@/components/ui/label";
import type { Faculty } from "@/features/campus/api";
import { MAX_MENTIONS } from "@/features/issues/schemas";

/** Ticks the teachers an issue concerns, with a text search; at most {@link MAX_MENTIONS}. */
export function FacultyPicker({
  faculties,
  chosen,
  onChange,
  error,
}: {
  faculties: Faculty[];
  chosen: string[];
  onChange: (ids: string[]) => void;
  error?: string;
}) {
  const [text, setText] = useState("");
  const wanted = text.trim().toLowerCase();
  const shown = faculties.filter((f) => !wanted || f.name.toLowerCase().includes(wanted) || f.code.toLowerCase().includes(wanted));
  return (
    <fieldset className="grid gap-2">
      <legend className="mb-1 text-sm font-medium">Teachers it concerns</legend>
      <Input aria-label="Search teachers" placeholder="Search teachers" value={text} onChange={(event) => setText(event.target.value)} />
      <div className="flex max-h-40 flex-col gap-2 overflow-y-auto p-1">
        {shown.map((f) => (
          <div key={f.id} className="flex items-center gap-2">
            <Checkbox
              id={`mention-${f.id}`}
              checked={chosen.includes(f.id)}
              disabled={!chosen.includes(f.id) && chosen.length >= MAX_MENTIONS}
              onCheckedChange={(on) => onChange(on === true ? [...chosen, f.id] : chosen.filter((id) => id !== f.id))}
            />
            <Label htmlFor={`mention-${f.id}`}>{f.name}</Label>
          </div>
        ))}
        {shown.length === 0 && <p className="text-sm text-muted-foreground">No teacher matches.</p>}
      </div>
      {error && (
        <p role="alert" className="text-sm text-destructive">
          {error}
        </p>
      )}
    </fieldset>
  );
}
