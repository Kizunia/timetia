import { describe, expect, it } from "vitest";

import { describeChange, slotText } from "@/features/tickets/lib/describe";
import { exchangeCandidates } from "@/features/tickets/lib/exchange";
import { raisableKinds } from "@/features/tickets/lib/kinds";
import { BLOCKED, conflictsOf, remap } from "@/features/tickets/lib/problems";
import { buildCreateBody, buildEditBody } from "@/features/tickets/lib/request";
import { groupByCapacity, hasHod, toReviewers } from "@/features/tickets/lib/reviewers";
import { toTarget } from "@/features/tickets/lib/slot";
import { cancelSchema, reviewSchema, slotSchema } from "@/features/tickets/schemas";
import type { Lecture } from "@/features/timetable/api";
import { ApiError } from "@/lib/api/problem";
import { CONFLICT_LABEL } from "@/lib/labels";

import { change, ticket } from "./tickets-fixtures";

const slot = { date: "2026-07-09", startHour: 9, endHour: 10, mode: "IN_PERSON" as const, roomId: "r-2" };
const lecture = (overrides: Record<string, unknown> = {}) =>
  ({
    id: "l-1", date: "2099-01-05", startTime: "09:00:00", endTime: "10:00:00", sessionType: "THEORY", batchId: null,
    facultyId: "f-1", cancelled: false, extra: false, subjectCode: "OOP", facultyName: "Dr Rao", ...overrides,
  }) as unknown as Lecture;

describe("slot and request bodies", () => {
  it("sends hours as HH:00 and only the room or the link that applies", () => {
    expect(toTarget({ ...slot, meetingLink: "https://x" })).toEqual({ date: "2026-07-09", startTime: "09:00", endTime: "10:00", mode: "IN_PERSON", roomId: "r-2" });
    expect(toTarget({ ...slot, mode: "ONLINE", meetingLink: " https://meet " })).toEqual({ date: "2026-07-09", startTime: "09:00", endTime: "10:00", mode: "ONLINE", meetingLink: "https://meet" });
    expect(toTarget({ ...slot, mode: "ONLINE", meetingLink: "  " })).not.toHaveProperty("meetingLink");
  });

  it("builds each kind with only the fields it takes", () => {
    const values = { ...slot, description: " ", reason: "", substituteFacultyId: "f-2", otherLectureId: "l-2", assignmentId: "a-1" };
    const keys = (kind: Parameters<typeof buildCreateBody>[0]) => Object.keys(JSON.parse(JSON.stringify(buildCreateBody(kind, "l-1", values)))).sort();
    expect(keys("RESCHEDULE")).toEqual(["lectureId", "target", "type"]);
    expect(keys("CANCEL")).toEqual(["lectureId", "type"]); // no reason given: the form's own check stops it earlier
    expect(keys("SUBSTITUTE")).toEqual(["lectureId", "substituteFacultyId", "type"]);
    expect(keys("EXCHANGE")).toEqual(["lectureId", "otherLectureId", "type"]);
    expect(keys("EXTRA")).toEqual(["assignmentId", "target", "type"]);
    expect(buildEditBody("EXCHANGE", 3, values)).toEqual({ version: 3 });
    expect(buildEditBody("RESCHEDULE", 3, { ...values, reason: "Exam" })).toMatchObject({ version: 3, reason: "Exam", target: { roomId: "r-2" } });
  });
});

describe("form schemas", () => {
  it("wants a room in person, an end after the start, and a reason for a cancellation or a rejection", () => {
    expect(slotSchema.safeParse(slot).success).toBe(true);
    expect(slotSchema.safeParse({ ...slot, roomId: undefined }).error?.issues[0]?.message).toBe("Choose a room");
    expect(slotSchema.safeParse({ ...slot, endHour: 9 }).error?.issues[0]?.message).toBe("End must be after start");
    expect(cancelSchema.safeParse({ reason: "  " }).error?.issues[0]?.message).toBe("Say why");
    expect(reviewSchema.safeParse({ decision: "REJECT" }).error?.issues[0]?.message).toBe("Say why");
    expect(reviewSchema.safeParse({ decision: "APPROVE" }).success).toBe(true);
  });
});

describe("problems", () => {
  it("names the form's fields and turns a blocked answer into conflicts", () => {
    const refused = new ApiError(400, { type: "x", title: "t", status: 400, code: "platform.validation_failed", errors: [{ field: "target.roomId", code: "room_required", message: "m" }] });
    expect((remap(refused) as ApiError).problem?.errors?.[0]?.field).toBe("roomId");
    const blocked = new ApiError(409, { type: "x", title: "t", status: 409, code: BLOCKED, errors: [{ field: "conflicts", code: "room_occupied", message: "R101 is taken" }] });
    expect(conflictsOf(blocked)).toEqual([{ type: "ROOM_OCCUPIED", message: "R101 is taken" }]);
    expect(conflictsOf(refused)).toEqual([]);
    expect(CONFLICT_LABEL.ROOM_OCCUPIED).toBe("Room is taken");
  });
});

describe("describing a change", () => {
  it("says from and to, and copes with missing names", () => {
    const t = ticket();
    expect(describeChange(t.change, t)).toEqual({ headline: "Move OOP", from: "Mon 6 Jul · 09:00–10:00 · R101", to: "Thu 9 Jul · 09:00–10:00 · R102" });
    const online = { date: "2026-07-08", startTime: "14:00:00", endTime: "15:00:00", mode: "ONLINE" as const };
    expect(slotText(online)).toBe("Wed 8 Jul · 14:00–15:00 · Online");
    const cancel = ticket({ type: "CANCEL" });
    expect(describeChange(change({ operation: "CANCEL", reason: "Away", target: null }) as never, cancel)).toEqual({ headline: "Cancel OOP on Mon 6 Jul", detail: "Away" });
    const sub = describeChange(change({ operation: "REASSIGN_FACULTY", substituteFacultyName: null, beforeFacultyName: null }) as never, ticket());
    expect(sub).toMatchObject({ from: "Dr Rao", to: undefined });
  });
});

describe("who can be asked, and what to offer", () => {
  const people = [
    { userId: "a", name: "Hod", capacity: "HOD" as const },
    { userId: "b", name: "Cr", capacity: "CR" as const },
  ];
  it("maps the chosen people back to their capacity and checks for an HOD", () => {
    expect(toReviewers(people, ["b"])).toEqual([{ userId: "b", capacity: "CR" }]);
    expect(hasHod(people, ["b"])).toBe(false);
    expect(hasHod(people, ["a", "b"])).toBe(true);
    expect(groupByCapacity(people).map((g) => g.capacity)).toEqual(["HOD", "CR"]);
  });

  it("offers kinds by permission, teaching and the state of the lecture", () => {
    const teacher = ["rescheduling.tickets.create", "rescheduling.tickets.cancel"];
    expect(raisableKinds(lecture(), teacher, true)).toEqual(["RESCHEDULE", "CANCEL", "SUBSTITUTE", "EXCHANGE", "PERMANENT_CHANGE"]);
    expect(raisableKinds(lecture(), teacher, false)).toEqual([]);
    expect(raisableKinds(lecture(), ["rescheduling.tickets.create-on-behalf"], false)).toEqual(["RESCHEDULE", "SUBSTITUTE", "EXCHANGE", "PERMANENT_CHANGE"]);
    expect(raisableKinds(lecture({ extra: true }), teacher, true)).not.toContain("PERMANENT_CHANGE");
    expect(raisableKinds(lecture({ cancelled: true }), teacher, true)).toEqual([]);
    expect(raisableKinds(lecture({ date: "2000-01-01" }), teacher, true)).toEqual([]);
  });

  it("offers for an exchange only the same session and batch, by someone else, not cancelled, not started", () => {
    const mine = lecture();
    const all = [
      lecture({ id: "ok", facultyId: "f-2" }),
      lecture({ id: "same-teacher" }),
      lecture({ id: "lab", facultyId: "f-2", sessionType: "LAB" }),
      lecture({ id: "batch", facultyId: "f-2", batchId: "b-1" }),
      lecture({ id: "cancelled", facultyId: "f-2", cancelled: true }),
      lecture({ id: "past", facultyId: "f-2", date: "2000-01-01" }),
    ];
    expect(exchangeCandidates(all, mine).map((l) => l.id)).toEqual(["ok"]);
  });
});
