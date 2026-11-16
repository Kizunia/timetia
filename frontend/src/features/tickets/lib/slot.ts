import type { components } from "@/lib/api/schema.gen";
import { hourOf, toApiTime } from "@/lib/dates";

export type TargetRequest = components["schemas"]["TargetRequest"];

/** The hours a lecture can start in: the day runs from 08:00 to 20:00 on the hour. */
export const HOURS = [8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19] as const;

/** What a slot form holds: hours as numbers, because the form picks an hour and the API wants `"09:00"`. */
export interface SlotValues {
  date: string;
  startHour: number;
  endHour: number;
  mode: "IN_PERSON" | "ONLINE";
  roomId?: string;
  meetingLink?: string;
}

/**
 * The target the API wants. An online lecture has no room and an in-person one has no link, and the server refuses the
 * other combination, so the field that does not apply is left out; an empty link is left out too.
 */
export function toTarget(values: SlotValues): TargetRequest {
  const target: TargetRequest = {
    date: values.date,
    startTime: toApiTime(values.startHour),
    endTime: toApiTime(values.endHour),
    mode: values.mode,
  };
  if (values.mode === "IN_PERSON" && values.roomId) target.roomId = values.roomId;
  const link = values.meetingLink?.trim();
  if (values.mode === "ONLINE" && link) target.meetingLink = link;
  return target;
}

/** The form values for a slot that already exists: a lecture as it is, or the target of a ticket. */
export function slotDefaults(place: {
  date: string;
  startTime: string;
  endTime: string;
  mode: "IN_PERSON" | "ONLINE";
  roomId?: string | null;
  meetingLink?: string | null;
}): SlotValues {
  return {
    date: place.date,
    startHour: hourOf(place.startTime),
    endHour: hourOf(place.endTime),
    mode: place.mode,
    roomId: place.roomId ?? undefined,
    meetingLink: place.meetingLink ?? undefined,
  };
}

/** How many hours a lecture lasts. A move keeps this length, which is why the end hour of a reschedule is not chosen. */
export function lectureHours(lecture: { startTime: string; endTime: string }): number {
  return hourOf(lecture.endTime) - hourOf(lecture.startTime);
}
