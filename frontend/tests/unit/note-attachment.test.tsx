// @vitest-environment jsdom
import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";

import { attachmentUrl } from "@/features/notes/api";
import { NoteAttachment } from "@/features/notes/NoteAttachment";
import type { Note } from "@/features/notes/api";

import { json, meWith, problem, renderScreen, stubApi } from "./ui";

const { success } = vi.hoisted(() => ({ success: vi.fn() }));
vi.mock("sonner", () => ({ toast: { success, error: vi.fn() } }));

const VERSION = "33333333-3333-7333-8333-333333333333";

function note(overrides: Partial<Note> = {}): Note {
  return {
    id: "n-1",
    ownerId: "11111111-1111-7111-8111-111111111111",
    title: "Shopping",
    body: "Milk",
    version: 3,
    createdAt: "2026-11-01T10:00:00Z",
    updatedAt: "2026-11-02T10:00:00Z",
    attachmentVersion: null,
    ...overrides,
  };
}

function picture(name = "milk.png", type = "image/png", bytes = 100): File {
  return new File([new Uint8Array(bytes)], name, { type });
}

describe("where a note's picture is served from", () => {
  it("is this application's own address with the size and the version, never a storage address", () => {
    expect(attachmentUrl("n-1", "preview", VERSION)).toBe(`/api/v1/notes/n-1/attachment?size=PREVIEW&v=${VERSION}`);
    expect(attachmentUrl("n-1", "full", VERSION)).toContain("size=FULL");
  });
});

describe("the picture of a note", () => {
  beforeEach(() => success.mockClear());

  it("shows nothing where files are switched off", () => {
    const { container } = renderScreen(<NoteAttachment note={note({ attachmentVersion: VERSION })} canChange />, meWith([], { filesEnabled: false }));

    expect(container).toBeEmptyDOMElement();
  });

  it("shows nothing for a note with no picture to someone who cannot add one", () => {
    const { container } = renderScreen(<NoteAttachment note={note()} canChange={false} />, meWith([]));

    expect(container).toBeEmptyDOMElement();
  });

  it("shows the picture with a description, linked to the full size, and offers no change to someone who may not", () => {
    renderScreen(<NoteAttachment note={note({ attachmentVersion: VERSION })} canChange={false} />, meWith([]));

    const image = screen.getByTestId("note-picture");
    expect(image).toHaveAttribute("src", attachmentUrl("n-1", "preview", VERSION));
    expect(image).toHaveAttribute("alt", "Picture attached to the note “Shopping”");
    expect(image.closest("a")).toHaveAttribute("href", attachmentUrl("n-1", "full", VERSION));
    expect(screen.queryByRole("button", { name: /picture/i })).not.toBeInTheDocument();
  });

  it("offers to add a picture to a note that has none, and to replace or remove one it has", () => {
    const { unmount } = renderScreen(<NoteAttachment note={note()} canChange />, meWith([]));
    expect(screen.getByRole("button", { name: "Add a picture" })).toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Remove picture" })).not.toBeInTheDocument();
    expect(screen.getByTestId("note-no-picture")).toBeInTheDocument();
    unmount();

    renderScreen(<NoteAttachment note={note({ attachmentVersion: VERSION })} canChange />, meWith([]));
    expect(screen.getByRole("button", { name: "Replace picture" })).toBeInTheDocument();
    expect(screen.getByRole("button", { name: "Remove picture" })).toBeInTheDocument();
  });

  it("refuses a file of the wrong kind or size before sending anything", async () => {
    const stub = stubApi(() => json(note()));
    renderScreen(<NoteAttachment note={note()} canChange />, meWith([]));
    const user = userEvent.setup({ applyAccept: false });

    await user.upload(screen.getByTestId("attachment-input"), picture("notes.txt", "text/plain"));
    expect(await screen.findByTestId("attachment-refusal")).toHaveTextContent("JPEG, PNG or WebP");

    await user.upload(screen.getByTestId("attachment-input"), picture("huge.png", "image/png", 5 * 1024 * 1024 + 1));
    expect(screen.getByTestId("attachment-refusal")).toHaveTextContent("smaller than 5 MB");
    expect(stub.calls).toHaveLength(0);
  });

  it("sends a good picture as a form to the note's attachment address", async () => {
    const stub = stubApi(() => json(note({ attachmentVersion: VERSION })));
    renderScreen(<NoteAttachment note={note()} canChange />, meWith([]));

    await userEvent.upload(screen.getByTestId("attachment-input"), picture());

    await waitFor(() => expect(success).toHaveBeenCalledWith("Picture saved"));
    const upload = stub.calls.find((call) => call.method === "PUT")!;
    expect(upload.path).toBe("/api/v1/notes/n-1/attachment");
    expect(upload.headers.get("content-type")).toMatch(/^multipart\/form-data/);
  });

  it("removes the picture", async () => {
    const stub = stubApi(() => new Response(null, { status: 204 }));
    renderScreen(<NoteAttachment note={note({ attachmentVersion: VERSION })} canChange />, meWith([]));

    await userEvent.click(screen.getByRole("button", { name: "Remove picture" }));

    await waitFor(() => expect(success).toHaveBeenCalledWith("Picture removed"));
    expect(stub.calls.find((call) => call.method === "DELETE")).toMatchObject({ path: "/api/v1/notes/n-1/attachment" });
  });

  it("says why a picture was not saved when the server refuses it", async () => {
    stubApi(() => problem(415, "files.unsupported_type"));
    renderScreen(<NoteAttachment note={note()} canChange />, meWith([]));

    await userEvent.upload(screen.getByTestId("attachment-input"), picture());

    expect(await screen.findByText("The picture was not saved")).toBeInTheDocument();
    expect(success).not.toHaveBeenCalled();
  });
});
