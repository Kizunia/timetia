"use client";

import { LectureActions } from "@/features/tickets/components/LectureActions";
import { TimetableScreen } from "@/features/timetable/TimetableScreen";

/**
 * The timetable with the ticket actions in its lecture sheet. The timetable feature never imports the tickets feature; this
 * is the place where the two meet. It is a client component because a function (what to render for a lecture) cannot be
 * handed from a server page to a client screen.
 */
export function TimetableWithTickets() {
  return <TimetableScreen renderActions={(lecture) => <LectureActions lecture={lecture} />} />;
}
