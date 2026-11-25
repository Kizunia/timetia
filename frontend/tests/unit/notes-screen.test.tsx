// @vitest-environment jsdom
import { screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { NotesScreen } from "@/features/notes/NotesScreen";

import { json, meWith, problem, renderScreen, stubApi, type Call } from "./ui";

const { success } = vi.hoisted(() => ({ success: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));

const ME = "11111111-1111-7111-8111-111111111111";
const OTHER = "22222222-2222-7222-8222-222222222222";

function note(id: string, title: string, overrides: Record<string, unknown> = {}) {
  return {
    id,
    ownerId: ME,
    title,
    body: "Body",
    version: 0,
    createdAt: "2026-11-01T10:00:00Z",
    updatedAt: "2026-11-02T10:00:00Z",
    attachmentVersion: null,
    ...overrides,
  };
}

function page(items: unknown[]) {
  return { items, page: { number: 0, size: 20, totalElements: items.length, totalPages: items.length ? 1 : 0 } };
}

const lists = (calls: Call[]) => calls.filter((call) => call.method === "GET" && call.path === "/api/v1/notes");

const reader = () => meWith(["notes.notes.create", "notes.notes.view"]);

describe("the notes list", () => {
  beforeEach(() => success.mockClear());

  it("shows the caller's own notes, asking the API for only those, newest first", async () => {
    const stub = stubApi(() => json(page([note("n-1", "Shopping"), note("n-2", "Ideas", { attachmentVersion: "v-1" })])));
    renderScreen(<NotesScreen />, reader());

    expect(await screen.findByRole("link", { name: "Shopping" })).toHaveAttribute("href", "/notes/n-1");
    expect(screen.getAllByTestId("note-row")).toHaveLength(2);
    expect(screen.getByText("Picture")).toBeInTheDocument();
    const asked = lists(stub.calls)[0]!;
    expect(asked.query.get("owner")).toBe(ME);
    expect(asked.query.get("sort")).toBe("createdAt,desc");
    expect(asked.query.get("size")).toBe("20");
    expect(asked.query.has("deleted")).toBe(false);
    expect(screen.queryByRole("columnheader", { name: "Written by" })).not.toBeInTheDocument();
  });

  it("shows no picture marker where files are switched off", async () => {
    stubApi(() => json(page([note("n-2", "Ideas", { attachmentVersion: "v-1" })])));
    renderScreen(<NotesScreen />, meWith(["notes.notes.view"], { filesEnabled: false }));

    await screen.findByRole("link", { name: "Ideas" });
    expect(screen.queryByText("Picture")).not.toBeInTheDocument();
  });

  it("offers New note only to someone who may create notes", async () => {
    stubApi(() => json(page([])));
    const { unmount } = renderScreen(<NotesScreen />, reader());
    expect(await screen.findByRole("link", { name: "New note" })).toHaveAttribute("href", "/notes/new");
    unmount();

    stubApi(() => json(page([])));
    renderScreen(<NotesScreen />, meWith(["notes.notes.view"]));
    await screen.findByText("There are no notes yet.");
    expect(screen.queryByRole("link", { name: "New note" })).not.toBeInTheDocument();
  });

  it("offers no choice of view to someone who may see only their own notes", async () => {
    stubApi(() => json(page([note("n-1", "Shopping")])));
    renderScreen(<NotesScreen />, reader());

    await screen.findByRole("link", { name: "Shopping" });
    expect(screen.queryByLabelText("Show")).not.toBeInTheDocument();
  });

  it("lets someone who may view any note switch to everyone's, which drops the owner filter and shows who wrote each", async () => {
    const stub = stubApi((call) =>
      call.query.has("owner") ? json(page([note("n-1", "Mine")])) : json(page([note("n-1", "Mine"), note("n-3", "Theirs", { ownerId: OTHER })])),
    );
    renderScreen(<NotesScreen />, meWith(["notes.notes.view", "notes.notes.view-any"]));
    await screen.findByRole("link", { name: "Mine" });

    await userEvent.click(screen.getByLabelText("Show"));
    await userEvent.click(await screen.findByRole("option", { name: "Everyone's notes" }));

    expect(await screen.findByRole("link", { name: "Theirs" })).toBeInTheDocument();
    expect(screen.getByRole("columnheader", { name: "Written by" })).toBeInTheDocument();
    const row = screen.getByRole("link", { name: "Theirs" }).closest("tr")!;
    expect(within(row).getByText(`User ${OTHER.slice(0, 8)}`)).toBeInTheDocument();
    expect(lists(stub.calls).at(-1)!.query.has("owner")).toBe(false);
  });

  it("lets someone who may restore notes see the deleted ones and bring one back", async () => {
    let restored = false;
    const stub = stubApi((call) => {
      if (call.method === "POST") {
        restored = true;
        return json(note("n-9", "Gone"));
      }
      if (call.query.get("deleted") === "true") return json(page(restored ? [] : [note("n-9", "Gone", { ownerId: OTHER })]));
      return json(page([]));
    });
    renderScreen(<NotesScreen />, meWith(["notes.notes.view", "notes.notes.restore"]));
    await screen.findByText("There are no notes yet.");

    await userEvent.click(screen.getByLabelText("Show"));
    await userEvent.click(await screen.findByRole("option", { name: "Deleted notes" }));

    // The title is also in the button's hidden text, so the row is found through the button.
    const row = (await screen.findByRole("button", { name: /Restore.*Gone/ })).closest("tr")!;
    expect(within(row).getByText("Gone", { selector: "span.font-medium" })).toBeInTheDocument();
    expect(within(row).queryByRole("link")).not.toBeInTheDocument();
    expect(lists(stub.calls).at(-1)!.query.has("owner")).toBe(false);
    await userEvent.click(within(row).getByRole("button", { name: /Restore/ }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Note restored"));
    expect(stub.calls.find((call) => call.method === "POST")).toMatchObject({ path: "/api/v1/notes/n-9/restore" });
    expect(await screen.findByText("There are no deleted notes.")).toBeInTheDocument();
  });

  it("searches for the text typed and shows when nothing matches", async () => {
    const stub = stubApi((call) => json(page(call.query.get("q") === "milk" ? [] : [note("n-1", "Shopping")])));
    renderScreen(<NotesScreen />, reader());
    await screen.findByRole("link", { name: "Shopping" });

    await userEvent.type(screen.getByLabelText("Search"), "  milk ");
    await userEvent.click(screen.getByRole("button", { name: "Search" }));

    expect(await screen.findByText("No notes match.")).toBeInTheDocument();
    const asked = lists(stub.calls).at(-1)!;
    expect(asked.query.get("q")).toBe("milk");
    expect(asked.query.get("page")).toBe("0");
  });

  it("says so when the list cannot be loaded", async () => {
    stubApi(() => problem(500, "platform.internal_error"));
    renderScreen(<NotesScreen />, reader());

    expect(await screen.findByText("The notes could not be loaded")).toBeInTheDocument();
  });
});
