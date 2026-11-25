"use client";

import { useRows, type Row } from "@/features/setup/api";
import type { OptionSource } from "@/features/setup/resources";
import { ROOM_TYPE_LABEL, SESSION_LABEL } from "@/lib/labels";

export interface Option {
  value: string;
  label: string;
  row?: Row;
}

export interface Lookups {
  options: (source: OptionSource) => Option[];
  /** The label of a record, or an em dash for one that is missing or deleted. */
  name: (source: OptionSource, id: string | null | undefined) => string;
}

const fixed = (labels: Record<string, string>): Option[] => Object.entries(labels).map(([value, label]) => ({ value, label }));

/** The records the campus forms choose from, so a table shows codes and names rather than ids. */
export function useLookups(): Lookups {
  const departments = useRows("departments");
  const divisions = useRows("divisions");
  const batches = useRows("batches");
  const subjects = useRows("subjects");
  const faculties = useRows("faculties");

  const of = (rows: Row[] | undefined, label: (row: Row) => string): Option[] =>
    (rows ?? []).map((row) => ({ value: row.id, label: label(row), row }));

  const all: Record<OptionSource, Option[]> = {
    departments: of(departments.data, (row) => row.code ?? ""),
    divisions: of(divisions.data, (row) => row.code ?? ""),
    batches: of(batches.data, (row) => row.code ?? ""),
    subjects: of(subjects.data, (row) => `${row.code} · ${row.name}`),
    faculties: of(faculties.data, (row) => row.name ?? ""),
    sessionTypes: fixed(SESSION_LABEL),
    roomTypes: fixed(ROOM_TYPE_LABEL),
  };

  return {
    options: (source) => all[source],
    name: (source, id) => all[source].find((option) => option.value === id)?.label ?? "—",
  };
}
