import type { components } from "@/lib/api/schema.gen";

type Schemas = components["schemas"];

/**
 * The words for the enumerations of the Timetia API. Each map is checked against the generated types with `satisfies`, so
 * a status or type the backend adds and this file lacks stops the build.
 */
export const TICKET_TYPE_LABEL = {
  RESCHEDULE: "Reschedule",
  CANCEL: "Cancellation",
  SUBSTITUTE: "Substitute",
  EXTRA: "Extra lecture",
  EXCHANGE: "Exchange",
  REVERT: "Revert",
  PERMANENT_CHANGE: "Permanent change",
} as const satisfies Record<Schemas["TicketResponse"]["type"], string>;

export const TICKET_STATUS_LABEL = {
  DRAFT: "Draft",
  IN_REVIEW: "In review",
  APPLIED: "Applied",
  REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn",
  EXPIRED: "Expired",
  REVERTED: "Reverted",
} as const satisfies Record<Schemas["TicketResponse"]["status"], string>;

/** The badge variant of each status (the variants of `components/ui/badge.tsx`). */
export const TICKET_STATUS_VARIANT = {
  DRAFT: "outline",
  IN_REVIEW: "secondary",
  APPLIED: "default",
  REJECTED: "destructive",
  WITHDRAWN: "ghost",
  EXPIRED: "ghost",
  REVERTED: "ghost",
} as const satisfies Record<Schemas["TicketResponse"]["status"], string>;

export const REVIEW_STATUS_LABEL = {
  PENDING: "Waiting",
  APPROVED: "Approved",
  REJECTED: "Rejected",
  ACKNOWLEDGED: "Acknowledged",
} as const satisfies Record<Schemas["ReviewResponse"]["status"], string>;

export const CAPACITY_LABEL = {
  HOD: "HOD",
  CR: "Class representative",
  BR: "Batch representative",
  AFFECTED_FACULTY: "Affected teacher",
  OTHER_FACULTY: "Other teacher",
} as const satisfies Record<Schemas["ReviewResponse"]["capacity"], string>;

/** What stands in the way of a change, in the words of domain.md section 3.5. */
export const CONFLICT_LABEL = {
  FACULTY_BUSY: "Teacher is busy",
  DIVISION_BUSY: "Division is busy",
  BATCH_BUSY: "Batch is busy",
  ROOM_OCCUPIED: "Room is taken",
  SAME_SLOT_TICKET: "Another ticket took this slot",
  OUTSIDE_CALENDAR: "Outside the calendar",
  OUTSIDE_SLOT_GRID: "Outside teaching hours",
  OCCURRENCE_CHANGED: "The lecture changed since",
} as const satisfies Record<Schemas["ConflictResponse"]["type"], string>;

export const SESSION_LABEL = {
  THEORY: "Theory",
  LAB: "Lab",
  TUTORIAL: "Tutorial",
} as const satisfies Record<Schemas["LiveLectureResponse"]["sessionType"], string>;

export const MODE_LABEL = {
  IN_PERSON: "In person",
  ONLINE: "Online",
} as const satisfies Record<Schemas["LiveLectureResponse"]["mode"], string>;

export const ROOM_TYPE_LABEL = {
  CLASSROOM: "Classroom",
  TUTORIAL_ROOM: "Tutorial room",
  LAB: "Lab",
} as const satisfies Record<Schemas["DirectoryRoomResponse"]["roomType"], string>;

/** The name of a weekday of the timetable; the index is `dayOfWeek`, 1 (Monday) to 6 (Saturday). */
export const DAY_LABEL = ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"] as const;
