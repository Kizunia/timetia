// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NoteEditor } from "@/features/notes/NoteEditor";

import { json, meWith, problem, renderScreen, stubApi, type Call } from "./ui";

const { success, push } = vi.hoisted(() => ({ success: vi.fn(), push: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));
vi.mock("next/navigation", () => ({ useRouter: () => ({ push, refresh: vi.fn() }) }));

const ME = "11111111-1111-7111-8111-111111111111";
const OTHER = "22222222-2222-7222-8222-222222222222";

function note(overrides: Record<string, unknown> = {}) {
  return {
    id: "n-1",
    ownerId: ME,
    title: "Shopping",
    body: "Milk",
    version: 3,
    createdAt: "2026-11-01T10:00:00Z",
    updatedAt: "2026-11-02T10:00:00Z",
    attachmentVersion: null,
    ...overrides,
  };
}

function api(current: ReturnType<typeof note>, onWrite?: (call: Call) => Response) {
  return stubApi((call) => {
    if (call.method === "GET") return json(current);
    return onWrite ? onWrite(call) : json({ ...current, version: current.version + 1 });
  });
}

const writer = () => meWith(["notes.notes.create", "notes.notes.view", "notes.notes.update", "notes.notes.delete"]);

describe("the note editor", () => {
  beforeEach(() => {
    success.mockClear();
    push.mockClear();
  });

  it("creates a note without asking for one that does not exist yet, and opens it afterwards", async () => {
    const stub = stubApi(() => json(note({ id: "n-new", title: "Ideas", body: "First", version: 0 }), 201));
    renderScreen(<NoteEditor />, writer());

    await userEvent.type(await screen.findByLabelText("Title"), "Ideas");
    await userEvent.type(screen.getByLabelText("Text"), "First");
    await userEvent.click(screen.getByRole("button", { name: "Create note" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/notes/n-new"));
    expect(success).toHaveBeenCalledWith("Note created");
    expect(stub.calls.find((call) => call.method === "POST")).toMatchObject({
      path: "/api/v1/notes",
      body: { title: "Ideas", body: "First" },
    });
    expect(stub.calls.filter((call) => call.method === "GET")).toHaveLength(0);
  });

  it("checks the title before sending", async () => {
    const stub = api(note());
    renderScreen(<NoteEditor noteId="n-1" />, writer());
    await screen.findByRole("heading", { name: "Shopping" });

    await userEvent.clear(screen.getByLabelText("Title"));
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    expect(await screen.findByText("Enter a title")).toBeInTheDocument();
    expect(stub.calls.some((call) => call.method === "PUT")).toBe(false);
  });

  it("saves with the version it started from, so a change made meanwhile is not overwritten", async () => {
    const stub = api(note());
    renderScreen(<NoteEditor noteId="n-1" />, writer());
    await screen.findByRole("heading", { name: "Shopping" });

    await userEvent.clear(screen.getByLabelText("Title"));
    await userEvent.type(screen.getByLabelText("Title"), "Groceries");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Note saved"));
    expect(stub.calls.find((call) => call.method === "PUT")).toMatchObject({
      path: "/api/v1/notes/n-1",
      body: { title: "Groceries", body: "Milk", version: 3 },
    });
  });

  it("says so under the title when the owner already has a note with it, and when someone else changed the note meanwhile", async () => {
    let answer = problem(409, "notes.duplicate_title");
    api(note(), () => answer);
    renderScreen(<NoteEditor noteId="n-1" />, writer());
    await screen.findByRole("heading", { name: "Shopping" });

    await userEvent.click(screen.getByRole("button", { name: "Save note" }));
    expect(await screen.findByText("You already have a note with this title.")).toBeInTheDocument();

    answer = problem(409, "platform.concurrent_modification");
    await userEvent.click(screen.getByRole("button", { name: "Save note" }));
    expect(await screen.findByText(/Someone else changed this while you were editing/)).toBeInTheDocument();
  });

  it("shows someone else's note read-only to a caller who may only view it", async () => {
    api(note({ ownerId: OTHER }));
    renderScreen(<NoteEditor noteId="n-1" />, meWith(["notes.notes.view", "notes.notes.view-any", "notes.notes.update", "notes.notes.delete"]));

    expect(await screen.findByRole("heading", { name: "Shopping" })).toBeInTheDocument();
    expect(screen.getByLabelText("Title")).toHaveAttribute("readonly");
    expect(screen.getByLabelText("Text")).toHaveAttribute("readonly");
    expect(screen.queryByRole("button", { name: "Save note" })).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Delete note" })).not.toBeInTheDocument();
    expect(screen.getByText("You can read this note but not change it.")).toBeInTheDocument();
  });

  it("lets someone who may change anyone's notes change and delete someone else's", async () => {
    api(note({ ownerId: OTHER }));
    renderScreen(<NoteEditor noteId="n-1" />, meWith(["notes.notes.view-any", "notes.notes.update-any", "notes.notes.delete-any"]));

    await screen.findByRole("heading", { name: "Shopping" });
    expect(screen.getByLabelText("Title")).not.toHaveAttribute("readonly");
    expect(screen.getByRole("button", { name: "Save note" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Delete note" })).toBeInTheDocument();
  });

  it("deletes after a confirmation, and goes back to the list", async () => {
    const stub = api(note(), () => new Response(null, { status: 204 }));
    renderScreen(<NoteEditor noteId="n-1" />, writer());
    await screen.findByRole("heading", { name: "Shopping" });

    await userEvent.click(screen.getByRole("button", { name: "Delete note" }));
    expect(stub.calls.some((call) => call.method === "DELETE")).toBe(false);
    await userEvent.click(await screen.findByRole("button", { name: "Delete" }));

    await waitFor(() => expect(push).toHaveBeenCalledWith("/notes"));
    expect(success).toHaveBeenCalledWith("Note deleted");
    expect(stub.calls.find((call) => call.method === "DELETE")).toMatchObject({ path: "/api/v1/notes/n-1" });
  });

  it("says so when the note does not exist or may not be seen, without saying which", async () => {
    stubApi(() => problem(404, "notes.not_found"));
    renderScreen(<NoteEditor noteId="n-404" />, writer());

    expect(await screen.findByText("The note could not be opened")).toBeInTheDocument();
    expect(screen.getByText("That note could not be found.")).toBeInTheDocument();
    expect(screen.queryByLabelText("Title")).not.toBeInTheDocument();
  });
});
