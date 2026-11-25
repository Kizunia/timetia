// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { visibleAdminSections } from "@/components/SiteHeader";
import { AddEntryForm } from "@/features/setup/components/AddEntryForm";
import { PublishButton } from "@/features/setup/components/PublishButton";
import { ResourceTable } from "@/features/setup/components/ResourceTable";
import { RESOURCES } from "@/features/setup/resources";
import { bodyOf, schemaFor } from "@/features/setup/schemas";

import { pageOf } from "./tickets-fixtures";
import { json, problem, renderScreen, stubApi, type Call } from "./ui";

const { success } = vi.hoisted(() => ({ success: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push: vi.fn() }) }));

const resource = (key: string) => RESOURCES.find((entry) => entry.key === key)!;
const ROOM = { id: "r-1", version: 0, code: "R101", name: "Room 101", roomType: "CLASSROOM", capacity: 60 };

function api(post: () => Response = () => json(ROOM, 201)) {
  return stubApi((call) => {
    if (call.method === "POST") return post();
    if (call.method === "DELETE") return new Response(null, { status: 204 });
    return json(pageOf(call.path === "/api/v1/rooms" ? [ROOM] : []));
  });
}

const sent = (calls: Call[], method: string) => calls.find((call) => call.method === method);

async function fillRoom() {
  await userEvent.click(await screen.findByRole("button", { name: "New room" }));
  await userEvent.type(screen.getByRole("textbox", { name: "Code" }), "R103");
  await userEvent.type(screen.getByRole("textbox", { name: "Name" }), "Room 103");
  await userEvent.click(screen.getByRole("combobox", { name: "Kind" }));
  await userEvent.click(screen.getByRole("option", { name: "Classroom" }));
  await userEvent.click(screen.getByRole("button", { name: "Create" }));
}

describe("a campus list", () => {
  it("creates a room, leaving out the seats that were not given", async () => {
    const stub = api();
    renderScreen(<ResourceTable resource={resource("rooms")} />);

    await fillRoom();

    await waitFor(() =>
      expect(sent(stub.calls, "POST")?.body).toEqual({ code: "R103", name: "Room 103", roomType: "CLASSROOM" }),
    );
  });

  it("puts a duplicate code under the code field", async () => {
    api(() => problem(409, "reference.duplicate_code"));
    renderScreen(<ResourceTable resource={resource("rooms")} />);

    await fillRoom();

    expect(await screen.findByText("Something with this code already exists.")).toBeInTheDocument();
    expect(screen.getByRole("textbox", { name: "Code" })).toHaveAttribute("aria-invalid", "true");
  });

  it("deletes only after a confirmation", async () => {
    const stub = api();
    renderScreen(<ResourceTable resource={resource("rooms")} />);

    await userEvent.click(await screen.findByRole("button", { name: "Delete R101" }));
    expect(sent(stub.calls, "DELETE")).toBeUndefined();
    await userEvent.click(screen.getByRole("button", { name: "Delete" }));

    await waitFor(() => expect(sent(stub.calls, "DELETE")?.path).toBe("/api/v1/rooms/r-1"));
  });

  it("asks a lab assignment for its batch, and sends none for theory", () => {
    const fields = resource("teaching-assignments").createFields;
    const values = { divisionId: "d", subjectId: "s", facultyId: "f", sessionType: "LAB", batchId: "" };

    expect(schemaFor(fields).safeParse(values).error?.issues.map((issue) => issue.message)).toEqual(["Choose the batch"]);
    expect(bodyOf(fields, { ...values, sessionType: "THEORY", batchId: "b" })).toEqual({
      divisionId: "d",
      subjectId: "s",
      facultyId: "f",
      sessionType: "THEORY",
    });
  });
});

describe("a draft timetable", () => {
  const form = () => (
    <AddEntryForm versionId="v-1" assignments={[{ value: "a-1", label: "OOP · Theory" }]} rooms={[{ value: "r-1", label: "R101" }]} />
  );

  async function add() {
    await userEvent.click(screen.getByRole("combobox", { name: "Lecture" }));
    await userEvent.click(screen.getByRole("option", { name: "OOP · Theory" }));
    await userEvent.click(screen.getByRole("combobox", { name: "Room" }));
    await userEvent.click(screen.getByRole("option", { name: "R101" }));
    await userEvent.click(screen.getByRole("button", { name: "Add lecture" }));
  }

  it("sends the weekly entry", async () => {
    const stub = stubApi(() => json({}, 201));
    renderScreen(form());

    await add();

    await waitFor(() =>
      expect(sent(stub.calls, "POST")).toMatchObject({
        path: "/api/v1/timetable/versions/v-1/entries",
        body: { assignmentId: "a-1", dayOfWeek: 1, startTime: "09:00", endTime: "10:00", mode: "IN_PERSON", roomId: "r-1" },
      }),
    );
  });

  it("shows the server's own words about a clash", async () => {
    stubApi(() => problem(409, "timetable.clash", { detail: "Dr Rao already teaches on Monday at 09:00." }));
    renderScreen(form());

    await add();

    expect(await screen.findByText("Dr Rao already teaches on Monday at 09:00.")).toBeInTheDocument();
  });

  it("publishes after a confirmation and says what it made", async () => {
    stubApi(() => json({ occurrencesCreated: 20, occurrencesSuperseded: 3, version: {} }));
    renderScreen(
      <PublishButton
        version={{ id: "v-1", divisionId: "d", termId: "t", effectiveFrom: "2026-07-06", status: "DRAFT", version: 0, createdAt: "", updatedAt: "" }}
      />,
    );

    await userEvent.click(screen.getByRole("button", { name: "Publish" }));
    await userEvent.click(screen.getByRole("button", { name: "Publish timetable" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Published: 20 lectures created, 3 replaced"));
  });
});

describe("the setup sections", () => {
  it("are shown to someone who manages the configuration", () => {
    const labels = visibleAdminSections({ permissions: ["timetable.config.manage"], administrator: false }).map((section) => section.label);

    expect(labels).toEqual(["Campus", "Timetables"]);
  });
});
