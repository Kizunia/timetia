import { expect, test, type BrowserContext, type Locator, type Page } from "@playwright/test";

import {
  ADMIN_USERNAME,
  adminPassword,
  DEMO_USERNAME,
  demoPassword,
  expectNoAccessibilityViolations,
  expectNoToasts,
  makeImage,
  signIn,
} from "./support";

test.skip(!demoPassword || !adminPassword, "DEMO_USER_PASSWORD and DEMO_ADMIN_PASSWORD are not set; run 'pnpm bootstrap'");

/**
 * The example feature against the real stack (ADR 0024, ADR 0043, ADR 0044, ADR 0051): a user writes, changes, finds and
 * pages through notes, attaches a picture that is kept whole and not cropped, what other people may not see or do is
 * refused in the page and, asked directly, by the API, and an administrator who may see everything deletes a note and
 * restores it with its picture. Every state is checked for accessibility. The steps share the users and the stack, so they
 * run in order, and every note carries a stamp of this run so the story can run again on the same stack.
 */
test.describe.configure({ mode: "serial" });

const STAMP = Date.now().toString(36);
const title = (name: string) => `${name} ${STAMP}`;

let userContext: BrowserContext;
let user: Page;
let adminContext: BrowserContext;
let admin: Page;
let noteId: string;
let adminNoteId: string;

test.beforeAll(async ({ browser }) => {
  userContext = await browser.newContext();
  user = await userContext.newPage();
  adminContext = await browser.newContext();
  admin = await adminContext.newPage();
});

test.afterAll(async () => {
  await userContext.close();
  await adminContext.close();
});

/** Calls the API from the page, as the page's own scripts do: with the session cookie and the CSRF header. */
async function api(page: Page, method: string, path: string, body?: unknown) {
  return page.evaluate(
    async ({ method, path, body }) => {
      const response = await fetch(path, {
        method,
        headers: { "x-timetia-request": "1", ...(body === undefined ? {} : { "content-type": "application/json" }) },
        body: body === undefined ? undefined : JSON.stringify(body),
      });
      const text = await response.text();
      return { status: response.status, json: text ? (JSON.parse(text) as Record<string, unknown>) : null };
    },
    { method, path, body },
  );
}

/** The picture has loaded from the API, at the size the server made it (width and height of the file). */
async function expectLoaded(image: Locator, width: number, height: number) {
  await expect
    .poll(() => image.evaluate((element: HTMLImageElement) => (element.complete ? `${element.naturalWidth}x${element.naturalHeight}` : "")), {
      message: "the note's picture to load",
    })
    .toBe(`${width}x${height}`);
}

const picture = (page: Page) => page.getByTestId("note-picture");
const rows = (page: Page) => page.getByTestId("note-row");

test("a user opens Notes from the header and sees an empty list that is easy to start from", async () => {
  await signIn(user, DEMO_USERNAME, demoPassword!);
  await expect(user.getByTestId("signed-in")).toBeVisible();

  await user.getByRole("banner").getByRole("link", { name: "Notes" }).click();
  await expect(user.getByRole("heading", { name: "Notes" })).toBeVisible();
  await expect(user.getByRole("link", { name: "New note" })).toBeVisible();
  // An ordinary user sees only their own notes, so there is nothing to switch between.
  await expect(user.getByLabel("Show")).toHaveCount(0);
  await expectNoAccessibilityViolations(user);
});

test("writing a note opens it, with a message for a title that is missing", async () => {
  await user.getByRole("link", { name: "New note" }).click();
  await expect(user.getByRole("heading", { name: "New note" })).toBeVisible();

  await user.getByRole("button", { name: "Create note" }).click();
  await expect(user.getByText("Enter a title")).toBeVisible();
  await expectNoAccessibilityViolations(user);

  await user.getByLabel("Title").fill(title("Weekend plan"));
  await user.getByLabel("Text").fill("Hike in the morning, read in the afternoon.");
  await user.getByRole("button", { name: "Create note" }).click();

  await expect(user.getByText("Note created")).toBeVisible();
  await expect(user).toHaveURL(/\/notes\/[0-9a-f-]{36}$/);
  noteId = user.url().split("/").at(-1)!;
  await expect(user.getByRole("heading", { name: title("Weekend plan") })).toBeVisible();
});

test("a second note with the same title is refused under the title, and the note can be changed and kept", async () => {
  const duplicate = await api(user, "POST", "/api/v1/notes", { title: title("Weekend plan"), body: "again" });
  expect(duplicate.status).toBe(409);
  expect(duplicate.json).toMatchObject({ code: "notes.duplicate_title" });

  await user.getByLabel("Text").fill("Hike in the morning, read in the afternoon, cook in the evening.");
  await user.getByRole("button", { name: "Save note" }).click();
  await expect(user.getByText("Note saved").last()).toBeVisible();

  await user.reload();
  await expect(user.getByLabel("Text")).toHaveValue(/cook in the evening/);
  await expectNoToasts(user);
  await expectNoAccessibilityViolations(user);
});

test("a picture is kept whole in proportion, replaced by another, refused when it is not an image, and removed", async () => {
  await expect(user.getByTestId("note-no-picture")).toBeVisible();

  // A wide picture stays wide: 1200x400 becomes 480x160 for the preview, not a 480x480 square.
  await user.getByTestId("attachment-input").setInputFiles({
    name: "panorama.png",
    mimeType: "image/png",
    buffer: await makeImage(user, "image/png", 1200, 400, "#2a7de1"),
  });
  await expect(user.getByText("Picture saved")).toBeVisible();
  await expectLoaded(picture(user), 480, 160);
  await expect(picture(user)).toHaveAttribute("alt", `Picture attached to the note “${title("Weekend plan")}”`);
  await expect(user.getByRole("button", { name: "Replace picture" })).toBeVisible();
  await expectNoToasts(user);
  await expectNoAccessibilityViolations(user);

  // Attaching a picture is not an edit of the text: typing that has not been saved yet survives it.
  await user.getByLabel("Title").fill(title("Weekend plan, in progress"));
  await user.getByTestId("attachment-input").setInputFiles({
    name: "portrait.jpg",
    mimeType: "image/jpeg",
    buffer: await makeImage(user, "image/jpeg", 400, 900, "#3c9d5d"),
  });
  await expect(user.getByText("Picture saved").last()).toBeVisible();
  await expectLoaded(picture(user), 213, 480);
  await expect(user.getByLabel("Title")).toHaveValue(title("Weekend plan, in progress"));
  await user.getByLabel("Title").fill(title("Weekend plan"));

  // The browser refuses what is not an image, and the API refuses it too when asked directly.
  await user.getByTestId("attachment-input").setInputFiles({ name: "notes.txt", mimeType: "text/plain", buffer: Buffer.from("hello") });
  await expect(user.getByTestId("attachment-refusal")).toContainText("JPEG, PNG or WebP");
  const direct = await user.evaluate(async (id) => {
    const form = new FormData();
    form.append("file", new Blob(["<?php echo 1;"], { type: "image/png" }), "evil.png");
    const response = await fetch(`/api/v1/notes/${id}/attachment`, { method: "PUT", headers: { "x-timetia-request": "1" }, body: form });
    return { status: response.status, code: ((await response.json()) as { code: string }).code };
  }, noteId);
  expect(direct).toEqual({ status: 415, code: "files.unsupported_type" });

  await user.getByRole("button", { name: "Remove picture" }).click();
  await expect(user.getByText("Picture removed")).toBeVisible();
  await expect(user.getByTestId("note-no-picture")).toBeVisible();
  await expect(picture(user)).toHaveCount(0);

  // Leave a picture on the note for the steps that follow.
  await user.getByTestId("attachment-input").setInputFiles({
    name: "panorama.png",
    mimeType: "image/png",
    buffer: await makeImage(user, "image/png", 1200, 400, "#d9534f"),
  });
  await expectLoaded(picture(user), 480, 160);
  await expectNoToasts(user);
  await expectNoAccessibilityViolations(user);
});

test("the list finds a note by its text, sorts, pages, and says when nothing matches", async () => {
  for (let index = 1; index <= 21; index++) {
    const created = await api(user, "POST", "/api/v1/notes", { title: title(`Paging ${String(index).padStart(2, "0")}`), body: "x" });
    expect(created.status).toBe(201);
  }
  await user.goto("/notes");
  await expect(rows(user).first()).toBeVisible();

  await user.getByLabel("Search").fill(`Paging`);
  await user.getByRole("button", { name: "Search" }).click();
  await expect(user.getByText(/Page 1 of 2 · 21 in all/)).toBeVisible();
  await expect(rows(user)).toHaveCount(20);
  await user.getByRole("button", { name: "Next" }).click();
  await expect(user.getByText(/Page 2 of 2 · 21 in all/)).toBeVisible();
  await expect(rows(user)).toHaveCount(1);

  await user.getByLabel("Sort by").click();
  await user.getByRole("option", { name: "Title A to Z" }).click();
  await expect(user.getByText(/Page 1 of 2/)).toBeVisible();
  await expect(rows(user).first()).toContainText(title("Paging 01"));

  await user.getByLabel("Search").fill(`no such text ${STAMP}`);
  await user.getByRole("button", { name: "Search" }).click();
  await expect(user.getByText("No notes match.")).toBeVisible();

  await user.getByLabel("Search").fill(title("Weekend plan"));
  await user.getByRole("button", { name: "Search" }).click();
  await expect(rows(user)).toHaveCount(1);
  await expect(rows(user).first().getByText("Picture")).toBeVisible();
  await expectNoAccessibilityViolations(user);
});

test("an administrator writes a note of their own, which the ordinary user cannot see or change", async () => {
  await signIn(admin, ADMIN_USERNAME, adminPassword!);
  await expect(admin.getByTestId("signed-in")).toBeVisible();
  const created = await api(admin, "POST", "/api/v1/notes", { title: title("Admin only"), body: "For administrators." });
  expect(created.status).toBe(201);
  adminNoteId = created.json!.id as string;
  await admin.goto(`/notes/${adminNoteId}`);
  await admin.getByTestId("attachment-input").setInputFiles({
    name: "private.png",
    mimeType: "image/png",
    buffer: await makeImage(admin, "image/png", 600, 300, "#8e44ad"),
  });
  await expect(admin.getByText("Picture saved")).toBeVisible();
  await expectLoaded(picture(admin), 480, 240);

  // The page says only that there is no such note, which is the same for a note that does not exist.
  await user.goto(`/notes/${adminNoteId}`);
  await expect(user.getByText("The note could not be opened")).toBeVisible();
  await expect(user.getByText("That note could not be found.")).toBeVisible();
  await expect(user.getByLabel("Title")).toHaveCount(0);
  await expectNoAccessibilityViolations(user);

  // Asked directly, the API refuses in the same way, for the note, its picture, and every way of changing it.
  expect((await api(user, "GET", `/api/v1/notes/${adminNoteId}`)).status).toBe(404);
  expect((await api(user, "GET", `/api/v1/notes/${adminNoteId}/attachment`)).status).toBe(404);
  expect((await api(user, "DELETE", `/api/v1/notes/${adminNoteId}/attachment`)).status).toBe(404);
  expect((await api(user, "PUT", `/api/v1/notes/${adminNoteId}`, { title: "x", body: "y", version: 0 })).status).toBe(404);
  expect((await api(user, "DELETE", `/api/v1/notes/${adminNoteId}`)).status).toBe(404);
});

test("what only administrators may do is not offered to the user, and the API refuses it when asked", async () => {
  await user.goto("/notes");
  await expect(user.getByRole("heading", { name: "Notes" })).toBeVisible();
  await expect(user.getByLabel("Show")).toHaveCount(0);
  await expect(user.getByRole("option", { name: "Deleted notes" })).toHaveCount(0);

  const deleted = await api(user, "GET", "/api/v1/notes?deleted=true");
  expect(deleted.status).toBe(403);
  expect(deleted.json).toMatchObject({ code: "platform.forbidden" });
  expect((await api(user, "POST", `/api/v1/notes/${noteId}/restore`)).status).toBe(403);
  // Asking for someone else's notes is not an error for a caller who may see only their own: the answer is an empty page.
  const everyone = await api(user, "GET", "/api/v1/notes?owner=22222222-2222-7222-8222-222222222222");
  expect(everyone.status).toBe(200);
  expect(everyone.json).toMatchObject({ items: [] });
  await expectNoAccessibilityViolations(user);
});

test("an administrator sees everyone's notes, deletes the user's note and restores it with its picture", async () => {
  await admin.goto("/notes");
  await admin.getByLabel("Show").click();
  await admin.getByRole("option", { name: "Everyone's notes" }).click();
  await admin.getByLabel("Search").fill(STAMP);
  await admin.getByRole("button", { name: "Search" }).click();
  await expect(admin.getByRole("columnheader", { name: "Written by" })).toBeVisible();
  const row = rows(admin).filter({ hasText: title("Weekend plan") });
  await expect(row).toHaveCount(1);
  await expect(row.getByText(/^User /)).toBeVisible();
  await expect(rows(admin).filter({ hasText: title("Admin only") }).getByText("You", { exact: true })).toBeVisible();
  await expectNoAccessibilityViolations(admin);

  await row.getByRole("link", { name: title("Weekend plan") }).click();
  await expect(admin.getByRole("heading", { name: title("Weekend plan") })).toBeVisible();
  await expect(admin.getByText("Written by someone else")).toBeVisible();
  await expectLoaded(picture(admin), 480, 160);
  await admin.getByRole("button", { name: "Delete note" }).click();
  await admin.getByRole("button", { name: "Delete", exact: true }).click();
  await expect(admin.getByText("Note deleted")).toBeVisible();
  await expect(admin).toHaveURL(/\/notes$/);

  // The owner no longer finds it, and nobody can read its picture while it is deleted.
  expect((await api(user, "GET", `/api/v1/notes/${noteId}`)).status).toBe(404);
  expect((await api(user, "GET", `/api/v1/notes/${noteId}/attachment`)).status).toBe(404);

  await admin.getByLabel("Show").click();
  await admin.getByRole("option", { name: "Deleted notes" }).click();
  await admin.getByLabel("Search").fill(title("Weekend plan"));
  await admin.getByRole("button", { name: "Search" }).click();
  const deleted = rows(admin).filter({ hasText: title("Weekend plan") });
  await expect(deleted).toHaveCount(1);
  await expectNoToasts(admin);
  await expectNoAccessibilityViolations(admin);
  await deleted.getByRole("button", { name: /Restore/ }).click();
  await expect(admin.getByText("Note restored")).toBeVisible();
  // The search is still set, so the empty list says that nothing matches it.
  await expect(admin.getByText("No notes match.")).toBeVisible();

  // Back where it was, with its picture.
  await user.goto(`/notes/${noteId}`);
  await expect(user.getByRole("heading", { name: title("Weekend plan") })).toBeVisible();
  await expectLoaded(picture(user), 480, 160);
  await expectNoToasts(user);
  await expectNoAccessibilityViolations(user);
});

test("the story leaves nothing behind that would show in the next run", async () => {
  const listed = await api(user, "GET", `/api/v1/notes?q=${STAMP}&size=100`);
  expect(listed.status).toBe(200);
  for (const note of (listed.json!.items as { id: string }[])) {
    expect((await api(user, "DELETE", `/api/v1/notes/${note.id}`)).status).toBe(204);
  }
  expect((await api(admin, "DELETE", `/api/v1/notes/${adminNoteId}`)).status).toBe(204);
  const after = await api(user, "GET", `/api/v1/notes?q=${STAMP}`);
  expect(after.json).toMatchObject({ items: [] });
});
