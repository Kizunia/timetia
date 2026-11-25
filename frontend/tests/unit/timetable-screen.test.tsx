// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { initialTimetableUi, useTimetableUi } from "@/features/timetable/store";
import { TimetableScreen } from "@/features/timetable/TimetableScreen";
import { addDays, formatDay, startOfWeek, todayIso } from "@/lib/dates";

import { json, meWith, problem, renderScreen, stubApi, type Call } from "./ui";

const { replace, search } = vi.hoisted(() => ({ replace: vi.fn(), search: { value: "" } }));
vi.mock("next/navigation", () => ({
  useRouter: () => ({ replace, push: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(search.value),
}));

const MONDAY = startOfWeek(todayIso());
const SUNDAY = addDays(MONDAY, 6);

const DIVISION = "d0000000-0000-7000-8000-000000000001";
const BATCH = "b0000000-0000-7000-8000-000000000001";
const RAO = "f0000000-0000-7000-8000-000000000001";
const ROY = "f0000000-0000-7000-8000-000000000002";

function lecture(id: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    date: MONDAY,
    startTime: "09:00:00",
    endTime: "10:00:00",
    sessionType: "THEORY",
    mode: "IN_PERSON",
    divisionId: DIVISION,
    divisionCode: "CSE-A",
    batchId: null,
    batchCode: null,
    subjectCode: "OOP",
    subjectName: "Object-Oriented Programming",
    facultyId: RAO,
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

function page(items: unknown[], number = 0, totalPages = 1) {
  return { items, page: { number, size: 100, totalElements: items.length, totalPages } };
}

const faculty = (id: string, name: string) => ({ id, code: name, name, departmentId: "p-1", hasAccount: true });

interface Campus {
  live?: (call: Call) => Response;
  myFaculty?: unknown[];
  lecture?: (call: Call) => Response;
}

/** The answers of a small campus; a test overrides only the live timetable, or who the caller is. */
function stubCampus({ live, myFaculty = [], lecture: single }: Campus = {}) {
  return stubApi((call) => {
    switch (call.path) {
      case "/api/v1/timetable/live":
        return live ? live(call) : json(page([]));
      case "/api/v1/directory/divisions":
        return json([{ id: DIVISION, code: "CSE-A", name: "SY CSE A", departmentId: "p-1" }]);
      case "/api/v1/directory/batches":
        return json(
          call.query.get("division")
            ? [{ id: BATCH, code: "CSE-A1", name: "A1", divisionId: DIVISION }]
            : [{ id: BATCH, code: "CSE-A1", name: "A1", divisionId: DIVISION }],
        );
      case "/api/v1/directory/faculties":
        return json([faculty(RAO, "Dr Rao"), faculty(ROY, "Dr Roy")]);
      case "/api/v1/directory/me":
        return json({ faculty: myFaculty, assignments: [] });
      case "/api/v1/me/scoped-roles":
        return json([]);
      default:
        if (call.path.startsWith("/api/v1/timetable/live/")) {
          return single ? single(call) : problem(404, "timetable.lecture_not_found");
        }
        return problem(404, "platform.not_found");
    }
  });
}

const liveCalls = (calls: Call[]) => calls.filter((call) => call.path === "/api/v1/timetable/live");

const viewer = () => meWith(["timetable.live.view-own"]);

beforeEach(() => {
  useTimetableUi.setState(initialTimetableUi());
  replace.mockClear();
  search.value = "";
});

describe("the timetable screen", () => {
  it("asks for the current week, 100 at a time, and shows each lecture with its flags", async () => {
    const stub = stubCampus({
      live: () =>
        json(
          page([
            lecture("l-1"),
            lecture("l-2", { subjectCode: "DBMS", startTime: "10:00:00", endTime: "11:00:00", cancelled: true, changed: true }),
            lecture("l-3", {
              subjectCode: "NET",
              date: addDays(MONDAY, 2),
              changed: true,
              originalDate: MONDAY,
              originalStartTime: "09:00:00",
              originalEndTime: "10:00:00",
            }),
          ]),
        ),
    });
    renderScreen(<TimetableScreen />, viewer());

    expect(await screen.findByRole("button", { name: /^OOP, Theory · CSE-A/ })).toBeInTheDocument();
    const asked = liveCalls(stub.calls)[0]!;
    expect(asked.query.get("from")).toBe(MONDAY);
    expect(asked.query.get("to")).toBe(SUNDAY);
    expect(asked.query.get("size")).toBe("100");

    const cancelled = screen.getByRole("button", { name: /^DBMS/ });
    expect(within(cancelled).getByText("Cancelled")).toBeInTheDocument();
    const moved = screen.getByRole("button", { name: /^NET/ });
    expect(within(moved).getByText("Moved")).toBeInTheDocument();
    expect(within(moved).getByText(`was ${formatDay(MONDAY)} 09:00`)).toBeInTheDocument();
  });

  it("reads every page of the week", async () => {
    const stub = stubCampus({
      live: (call) =>
        call.query.get("page") === "1"
          ? json(page([lecture("l-2", { subjectCode: "DBMS" })], 1, 2))
          : json(page([lecture("l-1")], 0, 2)),
    });
    renderScreen(<TimetableScreen />, viewer());

    expect(await screen.findByRole("button", { name: /^DBMS/ })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: /^OOP/ })).toBeInTheDocument();
    expect(liveCalls(stub.calls).map((call) => call.query.get("page"))).toEqual(["0", "1"]);
  });

  it("moves a week on with Next, and back with Prev and Today", async () => {
    const user = userEvent.setup();
    const stub = stubCampus();
    renderScreen(<TimetableScreen />, viewer());
    await screen.findByText("There are no lectures this week");

    await user.click(screen.getByRole("button", { name: "Next" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("from")).toBe(addDays(MONDAY, 7)));

    await user.click(screen.getByRole("button", { name: "Prev" }));
    await user.click(screen.getByRole("button", { name: "Prev" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("from")).toBe(addDays(MONDAY, -7)));

    await user.click(screen.getByRole("button", { name: "Today" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("from")).toBe(MONDAY));
  });

  it("sends the division and the batch that are chosen", async () => {
    const user = userEvent.setup();
    const stub = stubCampus();
    renderScreen(<TimetableScreen />, viewer());
    await screen.findByText("There are no lectures this week");

    await user.click(screen.getByRole("combobox", { name: "Division" }));
    await user.click(await screen.findByRole("option", { name: "CSE-A" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("division")).toBe(DIVISION));

    await user.click(screen.getByRole("combobox", { name: "Batch" }));
    await user.click(await screen.findByRole("option", { name: "CSE-A1" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("batch")).toBe(BATCH));
    expect(liveCalls(stub.calls)[0]!.query.has("division")).toBe(false);
  });

  it("offers Only my lectures to a person with a faculty record, and filters by that faculty", async () => {
    const user = userEvent.setup();
    const stub = stubCampus({ myFaculty: [faculty(RAO, "Dr Rao")] });
    renderScreen(<TimetableScreen />, viewer());

    await user.click(await screen.findByRole("checkbox", { name: "Only my lectures" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("faculty")).toBe(RAO));

    await user.click(screen.getByRole("checkbox", { name: "Only my lectures" }));
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.has("faculty")).toBe(false));
  });

  it("does not offer Only my lectures to a person with no faculty record", async () => {
    stubCampus({ myFaculty: [] });
    renderScreen(<TimetableScreen />, viewer());
    await screen.findByText("There are no lectures this week");

    expect(screen.queryByRole("checkbox", { name: "Only my lectures" })).not.toBeInTheDocument();
  });

  it("opens the lecture in a sheet with its details", async () => {
    const user = userEvent.setup();
    const stub = stubCampus({
      live: () => json(page([lecture("l-1")])),
      lecture: () =>
        json(
          lecture("l-1", {
            mode: "ONLINE",
            roomCode: null,
            meetingLink: "https://meet.example/abc",
            changed: true,
            originalFacultyId: ROY,
          }),
        ),
    });
    renderScreen(<TimetableScreen />, viewer());

    await user.click(await screen.findByRole("button", { name: /^OOP/ }));

    const sheet = await screen.findByRole("dialog");
    expect(await within(sheet).findByText("OOP: Object-Oriented Programming")).toBeInTheDocument();
    expect(within(sheet).getByRole("link", { name: "Join the meeting" })).toHaveAttribute("href", "https://meet.example/abc");
    expect(await within(sheet).findByText(/Substitute for Dr Roy/)).toBeInTheDocument();
    expect(stub.calls.some((call) => call.path === "/api/v1/timetable/live/l-1")).toBe(true);
  });

  it("says so when the lecture cannot be loaded", async () => {
    const user = userEvent.setup();
    stubCampus({ live: () => json(page([lecture("l-1")])) });
    renderScreen(<TimetableScreen />, viewer());

    await user.click(await screen.findByRole("button", { name: /^OOP/ }));

    expect(await screen.findByText("That lecture could not be found, or you may not see it.")).toBeInTheDocument();
  });

  it("opens the week and the sheet of a linked lecture, and drops the link when the sheet closes", async () => {
    const user = userEvent.setup();
    search.value = "lecture=l-9";
    const linkedDate = addDays(MONDAY, 21);
    const stub = stubCampus({ lecture: () => json(lecture("l-9", { date: linkedDate })) });
    renderScreen(<TimetableScreen />, viewer());

    const sheet = await screen.findByRole("dialog");
    expect(await within(sheet).findByText("OOP: Object-Oriented Programming")).toBeInTheDocument();
    await waitFor(() => expect(liveCalls(stub.calls).at(-1)!.query.get("from")).toBe(startOfWeek(linkedDate)));

    await user.click(within(sheet).getByRole("button", { name: "Close" }));
    expect(replace).toHaveBeenCalledWith("/timetable");
  });

  it("tells a person with no role in Timetia, when the week is empty", async () => {
    stubCampus();
    renderScreen(<TimetableScreen />, meWith([]));

    expect(await screen.findByText("There are no lectures this week")).toBeInTheDocument();
    expect(screen.getByText(/You have no role in Timetia yet/)).toBeInTheDocument();
  });

  it("does not say that to a person who has a role", async () => {
    stubCampus();
    renderScreen(<TimetableScreen />, viewer());

    expect(await screen.findByText("There are no lectures this week")).toBeInTheDocument();
    expect(screen.queryByText(/You have no role in Timetia yet/)).not.toBeInTheDocument();
  });

  it("shows an error when the timetable cannot be loaded", async () => {
    stubCampus({ live: () => problem(500, "platform.internal_error") });
    renderScreen(<TimetableScreen />, viewer());

    expect(await screen.findByText("The timetable could not be loaded")).toBeInTheDocument();
    expect(screen.getByText("Something went wrong on our side.")).toBeInTheDocument();
  });
});
