"use client";

import { Alert, AlertDescription } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import type { ReviewerCandidate } from "@/features/tickets/api";
import { groupByCapacity } from "@/features/tickets/lib/reviewers";
import { CAPACITY_LABEL } from "@/lib/labels";

/**
 * Lets the author choose whom to ask. The people come grouped by the capacity the server offers them in, and a person is
 * listed once. A permanent change needs an HOD, so when there is none to ask the picker says so instead of offering nothing.
 */
export function ReviewerPicker({
  candidates,
  value,
  onChange,
  requireHod,
}: {
  candidates: readonly ReviewerCandidate[];
  value: readonly string[];
  onChange: (value: string[]) => void;
  requireHod: boolean;
}) {
  const noHod = requireHod && !candidates.some((candidate) => candidate.capacity === "HOD");
  const toggle = (userId: string, on: boolean) =>
    onChange(on ? [...value.filter((id) => id !== userId), userId] : value.filter((id) => id !== userId));
  return (
    <div className="flex flex-col gap-4">
      {noHod && (
        <Alert>
          <AlertDescription>No HOD is available for this department. Ask an administrator.</AlertDescription>
        </Alert>
      )}
      {candidates.length === 0 && !noHod && <p className="text-sm text-muted-foreground">There is nobody to ask.</p>}
      {groupByCapacity(candidates).map((group) => (
        <fieldset key={group.capacity} className="flex flex-col gap-2">
          <legend className="mb-1 text-sm font-medium">{CAPACITY_LABEL[group.capacity]}</legend>
          {group.people.map((person) => {
            const id = `reviewer-${person.userId}`;
            return (
              <div key={person.userId} className="flex items-center gap-2">
                <Checkbox id={id} checked={value.includes(person.userId)} onCheckedChange={(on) => toggle(person.userId, on === true)} />
                <Label htmlFor={id}>{person.name}</Label>
              </div>
            );
          })}
        </fieldset>
      ))}
    </div>
  );
}
