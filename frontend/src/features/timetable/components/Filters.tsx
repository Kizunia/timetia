import { Checkbox } from "@/components/ui/checkbox";
import { Label } from "@/components/ui/label";
import { Select, SelectContent, SelectGroup, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import type { Batch, Division, Faculty } from "@/features/campus/api";

/** The select cannot hold an empty value, so "no filter" is this word. */
const ALL = "all";

export interface FilterValues {
  divisionId: string | null;
  batchId: string | null;
  facultyId: string | null;
  onlyMine: boolean;
}

/**
 * Narrows the week by division, batch and teacher, and, for a teacher, to their own lectures. It does not hide filters by
 * role: the server already limits the lectures to what the person may see.
 */
export function Filters({
  values,
  divisions,
  batches,
  faculties,
  canFilterMine,
  onChange,
}: {
  values: FilterValues;
  divisions: readonly Division[];
  batches: readonly Batch[];
  faculties: readonly Faculty[];
  /** Whether to offer "Only my lectures": the person has a faculty record. */
  canFilterMine: boolean;
  onChange: (change: Partial<FilterValues>) => void;
}) {
  const pick = (value: string) => (value === ALL ? null : value);
  return (
    <div className="flex flex-wrap items-end gap-4">
      <div className="flex flex-col gap-1">
        <Label htmlFor="filter-division">Division</Label>
        <Select value={values.divisionId ?? ALL} onValueChange={(v) => onChange({ divisionId: pick(v) })}>
          <SelectTrigger id="filter-division" className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL}>All divisions</SelectItem>
              {divisions.map((division) => (
                <SelectItem key={division.id} value={division.id}>
                  {division.code}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="filter-batch">Batch</Label>
        <Select
          value={values.batchId ?? ALL}
          disabled={!values.divisionId}
          onValueChange={(v) => onChange({ batchId: pick(v) })}
        >
          <SelectTrigger id="filter-batch" className="w-44">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL}>All batches</SelectItem>
              {batches.map((batch) => (
                <SelectItem key={batch.id} value={batch.id}>
                  {batch.code}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
      <div className="flex flex-col gap-1">
        <Label htmlFor="filter-teacher">Teacher</Label>
        <Select value={values.facultyId ?? ALL} onValueChange={(v) => onChange({ facultyId: pick(v), onlyMine: false })}>
          <SelectTrigger id="filter-teacher" className="w-48">
            <SelectValue />
          </SelectTrigger>
          <SelectContent>
            <SelectGroup>
              <SelectItem value={ALL}>All teachers</SelectItem>
              {faculties.map((faculty) => (
                <SelectItem key={faculty.id} value={faculty.id}>
                  {faculty.name}
                </SelectItem>
              ))}
            </SelectGroup>
          </SelectContent>
        </Select>
      </div>
      {canFilterMine && (
        <div className="flex items-center gap-2 pb-2">
          <Checkbox
            id="filter-mine"
            checked={values.onlyMine}
            onCheckedChange={(checked) => onChange({ onlyMine: checked === true })}
          />
          <Label htmlFor="filter-mine">Only my lectures</Label>
        </div>
      )}
    </div>
  );
}
