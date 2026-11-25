import { create } from "zustand";

import { startOfWeek, todayIso } from "@/lib/dates";

/** What the timetable screen remembers while it is open: ids and primitives only, never data from the API. */
export interface TimetableUi {
  /** The Monday of the week shown. */
  weekStart: string;
  divisionId: string | null;
  batchId: string | null;
  facultyId: string | null;
  /** Whether "Only my lectures" is ticked. */
  onlyMine: boolean;
  selectedLectureId: string | null;
  setWeekStart: (iso: string) => void;
  /** Changing the division clears the batch, because a batch belongs to one division. */
  setFilters: (filters: Partial<Pick<TimetableUi, "divisionId" | "batchId" | "facultyId" | "onlyMine">>) => void;
  select: (id: string | null) => void;
  reset: () => void;
}

export const initialTimetableUi = () => ({
  weekStart: startOfWeek(todayIso()),
  divisionId: null as string | null,
  batchId: null as string | null,
  facultyId: null as string | null,
  onlyMine: false,
  selectedLectureId: null as string | null,
});

export const useTimetableUi = create<TimetableUi>((set) => ({
  ...initialTimetableUi(),
  setWeekStart: (weekStart) => set({ weekStart }),
  setFilters: (filters) =>
    set((state) => {
      const changesDivision = "divisionId" in filters && filters.divisionId !== state.divisionId;
      return { ...filters, ...(changesDivision && !("batchId" in filters) ? { batchId: null } : {}) };
    }),
  select: (selectedLectureId) => set({ selectedLectureId }),
  reset: () => set(initialTimetableUi()),
}));
