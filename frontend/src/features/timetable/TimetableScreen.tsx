"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { useEffect, type ReactNode } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Button } from "@/components/ui/button";
import { Card, CardContent } from "@/components/ui/card";
import { Skeleton } from "@/components/ui/skeleton";
import { useBatches, useDivisions, useFaculties } from "@/features/campus/api";
import { useMyPlaces } from "@/features/campus/useMyPlaces";
import { usePermissions } from "@/features/identity/permissions";
import { useLecture, useLiveWeek, type Lecture } from "@/features/timetable/api";
import { Filters, type FilterValues } from "@/features/timetable/components/Filters";
import { LectureSheet } from "@/features/timetable/components/LectureSheet";
import { WeekGrid } from "@/features/timetable/components/WeekGrid";
import { useTimetableUi } from "@/features/timetable/store";
import { addDays, formatWeek, startOfWeek, todayIso } from "@/lib/dates";

const LIVE_PERMISSIONS = ["timetable.live.view", "timetable.live.view-own", "timetable.live.view-all"];

/**
 * The week's timetable, with week navigation, filters and a side sheet for the lecture that was clicked.
 *
 * @param renderActions what to show in the sheet for the open lecture, such as the buttons that raise a ticket
 */
export function TimetableScreen({ renderActions }: { renderActions?: (lecture: Lecture) => ReactNode }) {
  const router = useRouter();
  const linkedLecture = useSearchParams().get("lecture");
  const { hasAny } = usePermissions();

  const weekStart = useTimetableUi((s) => s.weekStart);
  const divisionId = useTimetableUi((s) => s.divisionId);
  const batchId = useTimetableUi((s) => s.batchId);
  const facultyId = useTimetableUi((s) => s.facultyId);
  const onlyMine = useTimetableUi((s) => s.onlyMine);
  const selectedLectureId = useTimetableUi((s) => s.selectedLectureId);
  const { setWeekStart, setFilters, select } = useTimetableUi.getState();

  const places = useMyPlaces();
  const divisions = useDivisions();
  const batches = useBatches(divisionId);
  const faculties = useFaculties();
  const week = useLiveWeek(weekStart, { division: divisionId, batch: batchId, faculty: facultyId });

  // A link to one lecture (`/timetable?lecture=<id>`) opens its week and its sheet, once the lecture is known.
  const linked = useLecture(linkedLecture);
  useEffect(() => {
    if (!linkedLecture || !linked.data) return;
    useTimetableUi.getState().setWeekStart(startOfWeek(linked.data.date));
    useTimetableUi.getState().select(linked.data.id);
  }, [linkedLecture, linked.data]);

  const close = () => {
    select(null);
    if (linkedLecture) router.replace("/timetable");
  };

  const change = (values: Partial<FilterValues>) => {
    if (values.onlyMine === true) setFilters({ onlyMine: true, facultyId: places.facultyIds[0] ?? null });
    else if (values.onlyMine === false && !("facultyId" in values)) setFilters({ onlyMine: false, facultyId: null });
    else setFilters(values);
  };

  const lectures = week.data ?? [];
  const unfiltered = !divisionId && !batchId && !facultyId;
  const hasRole = hasAny(...LIVE_PERMISSIONS);

  return (
    <section aria-labelledby="timetable-heading" className="flex flex-col gap-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h1 id="timetable-heading" data-testid="timetable-page" className="text-2xl font-semibold tracking-tight">
          Timetable
        </h1>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            Prev
          </Button>
          <Button variant="outline" size="sm" onClick={() => setWeekStart(startOfWeek(todayIso()))}>
            Today
          </Button>
          <Button variant="outline" size="sm" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Next
          </Button>
        </div>
      </div>
      <p aria-live="polite" className="text-sm text-muted-foreground">
        {formatWeek(weekStart)}
      </p>

      <Filters
        values={{ divisionId, batchId, facultyId, onlyMine }}
        divisions={divisions.data ?? []}
        batches={batches.data ?? []}
        faculties={faculties.data ?? []}
        canFilterMine={places.isFaculty}
        onChange={change}
      />

      {week.isLoading ? (
        <div aria-busy="true" className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6">
          {Array.from({ length: 6 }, (_, i) => (
            <Skeleton key={i} className="h-40" />
          ))}
        </div>
      ) : week.isError ? (
        <ErrorAlert error={week.error} title="The timetable could not be loaded" />
      ) : lectures.length === 0 && unfiltered ? (
        <Card>
          <CardContent className="flex flex-col gap-1 text-sm text-muted-foreground">
            <p>There are no lectures this week</p>
            {!hasRole && <p>You have no role in Timetia yet. Ask an administrator.</p>}
          </CardContent>
        </Card>
      ) : (
        <WeekGrid weekStart={weekStart} lectures={lectures} onSelect={select} />
      )}

      <LectureSheet lectureId={selectedLectureId} onClose={close} renderActions={renderActions} />
    </section>
  );
}
