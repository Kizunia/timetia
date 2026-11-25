import { beforeEach, describe, expect, it } from "vitest";

import { nameIndex } from "@/features/campus/lib/names";
import { placesOf } from "@/features/campus/lib/places";
import type { Lecture } from "@/features/timetable/api";
import { lectureAudience, lectureFlags, lectureTitle, lectureWhere } from "@/features/timetable/lib/describe";
import { groupByDay } from "@/features/timetable/lib/grid";
import { initialTimetableUi, useTimetableUi } from "@/features/timetable/store";

function lecture(overrides: Partial<Lecture> = {}): Lecture {
  return {
    id: "l-1",
    date: "2026-07-06",
    startTime: "09:00:00",
    endTime: "10:00:00",
    sessionType: "THEORY",
    mode: "IN_PERSON",
    divisionId: "d-1",
    divisionCode: "CSE-A",
    batchId: null,
    batchCode: null,
    subjectCode: "OOP",
    subjectName: "Object-Oriented Programming",
    facultyId: "f-1",
    facultyName: "Dr Rao",
    roomId: "r-1",
    roomCode: "R101",
    meetingLink: null,
    cancelled: false,
    changed: false,
    extra: false,
    originalDate: null,
    originalStartTime: null,
    originalEndTime: null,
    originalFacultyId: null,
    ...overrides,
  };
}

describe("nameIndex", () => {
  const names = nameIndex([{ id: "a", code: "CSE" }], (item) => item.code);

  it("answers the name of a known id", () => expect(names("a")).toBe("CSE"));

  it("answers a dash for an unknown, null or missing id", () => {
    expect(names("zzz")).toBe("—");
    expect(names(null)).toBe("—");
    expect(names(undefined)).toBe("—");
    expect(nameIndex(undefined, () => "x")("a")).toBe("—");
  });
});

describe("placesOf", () => {
  it("collects divisions, batches and departments from the scoped roles, and none for a global role", () => {
    const places = placesOf([
      { roleId: "1", roleName: "CR", scopeType: "DIVISION", scopeId: "d-1", scopeCode: "CSE-A" },
      { roleId: "2", roleName: "Student", scopeType: "BATCH", scopeId: "b-1", scopeCode: "CSE-A1" },
      { roleId: "3", roleName: "HOD", scopeType: "DEPARTMENT", scopeId: "p-1", scopeCode: "CSE" },
      { roleId: "4", roleName: "Faculty", scopeType: "GLOBAL", scopeId: null, scopeCode: null },
    ]);

    expect(places).toEqual({
      divisionIds: ["d-1"],
      batchIds: ["b-1"],
      departmentIds: ["p-1"],
      roles: ["CR", "Student", "HOD", "Faculty"],
    });
    expect(placesOf(undefined).roles).toEqual([]);
  });
});

describe("groupByDay", () => {
  it("gives six empty buckets for an empty week", () => {
    const days = groupByDay([], "2026-07-06");
    expect(days.map((day) => day.date)).toEqual([
      "2026-07-06",
      "2026-07-07",
      "2026-07-08",
      "2026-07-09",
      "2026-07-10",
      "2026-07-11",
    ]);
    expect(days.every((day) => day.lectures.length === 0)).toBe(true);
  });

  it("orders a day by start time, then division, then batch, then id", () => {
    const days = groupByDay(
      [
        lecture({ id: "c", startTime: "10:00:00" }),
        lecture({ id: "b", startTime: "09:00:00", divisionCode: "ME-A" }),
        lecture({ id: "a", startTime: "09:00:00", divisionCode: "CSE-A" }),
      ],
      "2026-07-06",
    );
    expect(days[0]!.lectures.map((l) => l.id)).toEqual(["a", "b", "c"]);
  });

  it("keeps two parallel labs of the same day, ordered by batch code", () => {
    const days = groupByDay(
      [
        lecture({ id: "lab2", date: "2026-07-07", sessionType: "LAB", batchCode: "CSE-A2" }),
        lecture({ id: "lab1", date: "2026-07-07", sessionType: "LAB", batchCode: "CSE-A1" }),
      ],
      "2026-07-06",
    );
    expect(days[1]!.lectures.map((l) => l.id)).toEqual(["lab1", "lab2"]);
  });

  it("keeps a cancelled lecture and drops one outside the six days", () => {
    const days = groupByDay(
      [lecture({ id: "x", cancelled: true }), lecture({ id: "sun", date: "2026-07-12" }), lecture({ id: "next", date: "2026-07-13" })],
      "2026-07-06",
    );
    expect(days[0]!.lectures.map((l) => l.id)).toEqual(["x"]);
    expect(days.flatMap((day) => day.lectures)).toHaveLength(1);
  });
});

describe("describing a lecture", () => {
  it("titles it with the subject code, or says the subject was deleted", () => {
    expect(lectureTitle(lecture())).toBe("OOP");
    expect(lectureTitle(lecture({ subjectCode: null }))).toBe("Deleted subject");
  });

  it("says where: the room, Online, or a dash", () => {
    expect(lectureWhere(lecture())).toBe("R101");
    expect(lectureWhere(lecture({ mode: "ONLINE", roomCode: null }))).toBe("Online");
    expect(lectureWhere(lecture({ roomCode: null }))).toBe("—");
  });

  it("says for whom: the division for theory, the batch for a lab", () => {
    expect(lectureAudience(lecture())).toBe("Theory · CSE-A");
    expect(lectureAudience(lecture({ sessionType: "LAB", batchCode: "CSE-A1" }))).toBe("Lab · CSE-A1");
    expect(lectureAudience(lecture({ sessionType: "TUTORIAL", batchCode: "CSE-A1" }))).toBe("Tutorial · CSE-A1");
  });

  it("flags a plain lecture with nothing", () => expect(lectureFlags(lecture())).toEqual([]));

  it("flags each kind of change", () => {
    expect(lectureFlags(lecture({ cancelled: true, changed: true }))).toEqual([{ label: "Cancelled", variant: "destructive" }]);
    expect(lectureFlags(lecture({ extra: true, changed: true }))).toEqual([{ label: "Extra", variant: "outline" }]);
    expect(lectureFlags(lecture({ changed: true, originalDate: "2026-07-06" }))).toEqual([
      { label: "Moved", variant: "secondary" },
    ]);
    expect(lectureFlags(lecture({ changed: true }))).toEqual([{ label: "Changed", variant: "secondary" }]);
    expect(lectureFlags(lecture({ changed: true, originalFacultyId: "f-9" }))).toEqual([
      { label: "Substitute", variant: "outline" },
    ]);
    expect(lectureFlags(lecture({ mode: "ONLINE" }))).toEqual([{ label: "Online", variant: "outline" }]);
  });

  it("lists several flags in a fixed order", () => {
    const flags = lectureFlags(lecture({ changed: true, originalDate: "2026-07-06", originalFacultyId: "f-9", mode: "ONLINE" }));
    expect(flags.map((flag) => flag.label)).toEqual(["Moved", "Substitute", "Online"]);
  });
});

describe("the timetable store", () => {
  beforeEach(() => useTimetableUi.setState(initialTimetableUi()));

  it("clears the batch when the division changes, and keeps it when the batch is set with it", () => {
    const { setFilters } = useTimetableUi.getState();
    setFilters({ divisionId: "d-1" });
    setFilters({ batchId: "b-1" });
    expect(useTimetableUi.getState().batchId).toBe("b-1");

    setFilters({ divisionId: "d-2" });
    expect(useTimetableUi.getState().batchId).toBeNull();

    setFilters({ divisionId: "d-3", batchId: "b-3" });
    expect(useTimetableUi.getState().batchId).toBe("b-3");
  });
});
