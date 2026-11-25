// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { RaiseDialog } from "@/features/tickets/components/RaiseDialog";
import type { Lecture } from "@/features/timetable/api";

import { ticket } from "./tickets-fixtures";
import { json, problem, renderScreen, stubApi } from "./ui";

const { push } = vi.hoisted(() => ({ push: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success: vi.fn(), error: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push }) }));

const LECTURE = {
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
} as unknown as Lecture;

function api(create: () => Response = () => json(ticket(), 201)) {
  return stubApi((call) => {
    if (call.path === "/api/v1/tickets" && call.method === "POST") return create();
    if (call.path === "/api/v1/timetable/free-rooms") return json([{ id: "r-1", code: "R101", roomType: "CLASSROOM", capacity: 60, fits: true }]);
    if (call.path === "/api/v1/directory/me") return json({ faculty: [], assignments: [] });
    return json([]);
  });
}

const posted = (calls: ReturnType<typeof api>["calls"]) => calls.find((call) => call.method === "POST")?.body;

describe("raising a ticket for a lecture", () => {
  beforeEach(() => push.mockClear());

  it("sends a reschedule's target and no empty reason, then goes to the ticket", async () => {
    const stub = api();
    renderScreen(<RaiseDialog kind="RESCHEDULE" lecture={LECTURE} open onOpenChange={() => {}} />);

    await userEvent.click(await screen.findByRole("button", { name: "Create ticket" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/tickets/t-1"));
    expect(posted(stub.calls)).toEqual({
      type: "RESCHEDULE",
      lectureId: "l-1",
      target: { date: "2026-07-06", startTime: "09:00", endTime: "10:00", mode: "IN_PERSON", roomId: "r-1" },
    });
  });

  it("wants a reason to cancel, and sends no target", async () => {
    const stub = api();
    renderScreen(<RaiseDialog kind="CANCEL" lecture={LECTURE} open onOpenChange={() => {}} />);

    await userEvent.click(await screen.findByRole("button", { name: "Create ticket" }));
    expect(await screen.findByText("Say why")).toBeInTheDocument();
    expect(posted(stub.calls)).toBeUndefined();

    await userEvent.type(screen.getByRole("textbox", { name: "Why is it cancelled?" }), "Holiday");
    await userEvent.click(screen.getByRole("button", { name: "Create ticket" }));
    await waitFor(() => expect(posted(stub.calls)).toEqual({ type: "CANCEL", lectureId: "l-1", reason: "Holiday" }));
  });

  it("does not offer the lecture's own teacher as their substitute", async () => {
    stubApi((call) =>
      call.path === "/api/v1/directory/faculties"
        ? json([
            { id: "f-1", code: "RAO", name: "Dr Rao", departmentId: "d", hasAccount: true },
            { id: "f-2", code: "ROY", name: "Dr Roy", departmentId: "d", hasAccount: true },
          ])
        : json({ faculty: [], assignments: [] }),
    );
    renderScreen(<RaiseDialog kind="SUBSTITUTE" lecture={LECTURE} open onOpenChange={() => {}} />);

    await userEvent.click(await screen.findByRole("combobox", { name: "Who takes the lecture" }));

    expect(await screen.findByRole("option", { name: "Dr Roy (ROY)" })).toBeInTheDocument();
    expect(screen.queryByRole("option", { name: "Dr Rao (RAO)" })).not.toBeInTheDocument();
  });

  it("puts a server's message about the room under the room", async () => {
    api(() => problem(400, "validation_failed", { errors: [{ field: "target.roomId", code: "room_required", message: "Choose a room that exists." }] }));
    renderScreen(<RaiseDialog kind="RESCHEDULE" lecture={LECTURE} open onOpenChange={() => {}} />);

    await userEvent.click(await screen.findByRole("button", { name: "Create ticket" }));

    expect(await screen.findByText("Choose a room that exists.")).toHaveAttribute("id", "slot-room-error");
    expect(push).not.toHaveBeenCalled();
  });
});
