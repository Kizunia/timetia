"use client";

import type { ReactNode } from "react";

import { ErrorAlert } from "@/components/ErrorAlert";
import { Badge } from "@/components/ui/badge";
import { Sheet, SheetContent, SheetDescription, SheetFooter, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { Skeleton } from "@/components/ui/skeleton";
import { useFaculties } from "@/features/campus/api";
import { nameIndex } from "@/features/campus/lib/names";
import { useLecture, type Lecture } from "@/features/timetable/api";
import { lectureAudience, lectureFlags, lectureTitle } from "@/features/timetable/lib/describe";
import { formatDay, formatDayLong, hhmm } from "@/lib/dates";

function Details({ lecture }: { lecture: Lecture }) {
  const faculties = useFaculties();
  const facultyName = nameIndex(faculties.data, (faculty) => faculty.name);
  return (
    <dl className="grid grid-cols-[6rem_1fr] gap-x-3 gap-y-2 px-4 text-sm">
      <dt className="text-muted-foreground">When</dt>
      <dd>
        {formatDayLong(lecture.date)}, {hhmm(lecture.startTime)}–{hhmm(lecture.endTime)}
      </dd>
      <dt className="text-muted-foreground">Where</dt>
      <dd>
        {lecture.mode === "ONLINE" ? (
          <>
            Online
            {lecture.meetingLink && (
              <>
                {" "}
                <a
                  href={lecture.meetingLink}
                  target="_blank"
                  rel="noreferrer"
                  className="font-medium text-primary underline underline-offset-2"
                >
                  Join the meeting
                </a>
              </>
            )}
          </>
        ) : (
          (lecture.roomCode ?? "—")
        )}
      </dd>
      <dt className="text-muted-foreground">Who</dt>
      <dd>
        {lecture.facultyName ?? "—"}
        {lecture.originalFacultyId && <> (Substitute for {facultyName(lecture.originalFacultyId)})</>}
      </dd>
      <dt className="text-muted-foreground">For</dt>
      <dd>{lectureAudience(lecture)}</dd>
      {lecture.originalDate && lecture.originalStartTime && lecture.originalEndTime && (
        <>
          <dt className="text-muted-foreground">Moved from</dt>
          <dd>
            {formatDay(lecture.originalDate)} {hhmm(lecture.originalStartTime)}–{hhmm(lecture.originalEndTime)}
          </dd>
        </>
      )}
    </dl>
  );
}

/**
 * The side panel of one lecture. `actions` is a slot: a higher feature (tickets) puts its buttons there, so this feature
 * never has to know about it.
 */
export function LectureSheet({
  lectureId,
  onClose,
  actions,
}: {
  lectureId: string | null;
  onClose: () => void;
  actions?: ReactNode;
}) {
  const query = useLecture(lectureId);
  const lecture = query.data;
  const title = lecture
    ? `${lectureTitle(lecture)}${lecture.subjectName ? `: ${lecture.subjectName}` : ""}`
    : "Lecture";
  return (
    <Sheet open={Boolean(lectureId)} onOpenChange={(open) => !open && onClose()}>
      <SheetContent side="right" aria-busy={query.isLoading}>
        <SheetHeader>
          <SheetTitle>{title}</SheetTitle>
          <SheetDescription className="sr-only">Details of the lecture</SheetDescription>
          {lecture && (
            <div className="flex flex-wrap gap-1">
              {lectureFlags(lecture).map((flag) => (
                <Badge key={flag.label} variant={flag.variant}>
                  {flag.label}
                </Badge>
              ))}
            </div>
          )}
        </SheetHeader>
        {query.isLoading && (
          <div className="flex flex-col gap-3 px-4">
            <Skeleton className="h-5 w-3/4" />
            <Skeleton className="h-5 w-1/2" />
            <Skeleton className="h-5 w-2/3" />
          </div>
        )}
        {query.isError && (
          <div className="px-4">
            <ErrorAlert error={query.error} title="The lecture could not be loaded" />
          </div>
        )}
        {lecture && <Details lecture={lecture} />}
        <SheetFooter>{actions}</SheetFooter>
      </SheetContent>
    </Sheet>
  );
}
