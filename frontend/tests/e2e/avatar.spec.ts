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
 * The avatar story against the real stack (ADR 0029, ADR 0041): a user uploads a photo in each of the three formats,
 * the page shows it in both sizes, what must be refused is refused by the browser and, asked directly, by the API, an
 * ordinary user cannot look at someone else's photo while an administrator can, and removing it brings the initials back.
 * The steps share the user and the stack, so they run in order.
 */
test.describe.configure({ mode: "serial" });

let userContext: BrowserContext;
let user: Page;
let adminContext: BrowserContext;
let admin: Page;
let userId: string;

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

/** The picture has loaded from the API, at the size the server made it. */
async function expectLoaded(image: Locator, pixels: number) {
  await expect
    .poll(() => image.evaluate((element: HTMLImageElement) => (element.complete ? element.naturalWidth : 0)), {
      message: "the avatar image to load",
    })
    .toBe(pixels);
}

const photo = (page: Page) => page.getByRole("img", { name: "Your photo" });
const headerAvatar = (page: Page) => page.getByRole("banner").getByTestId("avatar");

async function choose(page: Page, file: { name: string; mimeType: string; buffer: Buffer }) {
  await page.getByTestId("avatar-input").setInputFiles(file);
}

test("a user with no photo sees their initials, and can add one in the profile", async () => {
  await signIn(user, DEMO_USERNAME, demoPassword!);
  await expect(user.getByTestId("signed-in")).toBeVisible();
  userId = await user.evaluate(async () => ((await (await fetch("/api/v1/me")).json()) as { id: string }).id);

  await user.goto("/profile");
  await expect(user.getByRole("heading", { name: "Your profile" })).toBeVisible();
  // A photo from an earlier run would hide what this one shows, so start clean.
  if (await user.getByRole("button", { name: "Remove photo" }).isVisible()) {
    await user.getByRole("button", { name: "Remove photo" }).click();
    await expect(user.getByRole("button", { name: "Upload a photo" })).toBeVisible();
  }
  await expect(photo(user)).toContainText("DU");
  await expect(photo(user).locator("img")).toHaveCount(0);
  await expect(user.getByRole("button", { name: "Remove photo" })).toHaveCount(0);
  await expectNoAccessibilityViolations(user);
});

test("uploading a JPEG shows it in the page and in the header, at the sizes the server made", async () => {
  await choose(user, {
    name: "holiday.jpg",
    mimeType: "image/jpeg",
    buffer: await makeImage(user, "image/jpeg", 800, 450, "#2a7de1"),
  });

  await expect(user.getByText("Photo saved")).toBeVisible();
  await expectLoaded(photo(user).locator("img"), 256);
  await expectLoaded(headerAvatar(user).locator("img"), 64);
  await expect(user.getByRole("button", { name: "Replace photo" })).toBeVisible();
  await expect(user.getByRole("button", { name: "Remove photo" })).toBeVisible();
  await expectNoToasts(user);
  await expectNoAccessibilityViolations(user);
});

test("replacing it with a PNG and then a WebP each changes the picture", async () => {
  const sources: string[] = [];
  for (const [name, type, colour] of [
    ["round.png", "image/png", "#d9534f"],
    ["modern.webp", "image/webp", "#3c9d5d"],
  ] as const) {
    sources.push((await photo(user).locator("img").getAttribute("src")) ?? "");
    await choose(user, { name, mimeType: type, buffer: await makeImage(user, type, 300, 300, colour) });
    await expect(user.getByText("Photo saved").last()).toBeVisible();
    await expect.poll(async () => (await photo(user).locator("img").getAttribute("src")) ?? "").not.toBe(sources.at(-1));
    await expectLoaded(photo(user).locator("img"), 256);
  }
  expect(new Set(sources).size).toBe(2);
});

test("the browser refuses a wrong type or a big file without sending it, and says why", async () => {
  const uploads: string[] = [];
  user.on("request", (request) => {
    if (request.method() === "PUT" && request.url().includes("/api/v1/me/avatar")) uploads.push(request.url());
  });

  await user.getByTestId("avatar-input").setInputFiles({ name: "notes.txt", mimeType: "text/plain", buffer: Buffer.from("hello") });
  await expect(user.getByTestId("avatar-refusal")).toContainText("JPEG, PNG or WebP");
  await user.getByTestId("avatar-input").setInputFiles({
    name: "big.png",
    mimeType: "image/png",
    buffer: Buffer.alloc(6 * 1024 * 1024),
  });
  await expect(user.getByTestId("avatar-refusal")).toContainText("smaller than 5 MB");

  expect(uploads).toHaveLength(0);
});

test("asked directly, the API refuses a disguised file, one over the limit and one far over it", async () => {
  const answers = await user.evaluate(async () => {
    const send = async (blob: Blob, name: string) => {
      const form = new FormData();
      form.append("file", blob, name);
      const response = await fetch("/api/v1/me/avatar", { method: "PUT", body: form, headers: { "x-kibootia-request": "1" } });
      const body = (await response.json().catch(() => null)) as { code?: string } | null;
      return { status: response.status, code: body?.code };
    };
    return {
      // Called a PNG, but a script: the bytes decide, not the name or the declared type.
      disguised: await send(new Blob(["<script>alert(1)</script>"], { type: "image/png" }), "avatar.png"),
      overTheLimit: await send(new Blob([new Uint8Array(5 * 1024 * 1024 + 1024)], { type: "image/png" }), "big.png"),
      // Larger than the multipart ceiling: the proxy streams it, and Spring answers instead of the connection resetting.
      farOver: await send(new Blob([new Uint8Array(8 * 1024 * 1024)], { type: "image/png" }), "huge.png"),
    };
  });

  expect(answers.disguised).toEqual({ status: 415, code: "files.unsupported_type" });
  expect(answers.overTheLimit).toEqual({ status: 413, code: "files.too_large" });
  expect(answers.farOver.status).toBe(413);
  // None of them replaced the photo.
  await user.reload();
  await expectLoaded(photo(user).locator("img"), 256);
});

test("an ordinary user may see their own photo but not anyone else's, however they ask", async () => {
  const answers = await user.evaluate(async (own) => {
    const status = async (id: string) => (await fetch(`/api/v1/users/${id}/avatar?size=SMALL`)).status;
    const other = await fetch(`/api/v1/users/22222222-2222-7222-8222-222222222222/avatar`);
    return {
      own: await status(own),
      other: other.status,
      otherCode: ((await other.json()) as { code: string }).code,
    };
  }, userId);

  expect(answers).toEqual({ own: 200, other: 403, otherCode: "platform.forbidden" });
});

test("an administrator sees the user's photo on the user's page, and the page has no accessibility violations", async () => {
  await signIn(admin, ADMIN_USERNAME, adminPassword!);
  await expect(admin.getByTestId("signed-in")).toBeVisible();

  await admin.goto(`/admin/users/${userId}`);

  await expect(admin.getByRole("heading", { name: "Demo User" })).toBeVisible();
  await expectLoaded(admin.getByTestId("avatar").locator("img").first(), 256);
  // The list shows it too, found by search so that it does not matter how many other users the stack holds.
  await admin.goto("/admin/users");
  await admin.getByLabel("Search").fill("demo@kibootia.localhost");
  await admin.getByRole("button", { name: "Search" }).click();
  const row = admin.getByTestId("user-row").filter({ hasText: "demo@kibootia.localhost" });
  await expect(row).toHaveCount(1);
  await expect(row.getByTestId("avatar").locator("img")).toHaveCount(1);
  await expectNoAccessibilityViolations(admin);
});

test("removing the photo brings the initials back, and the API says there is none", async () => {
  await user.goto("/profile");
  await user.getByRole("button", { name: "Remove photo" }).click();

  await expect(user.getByText("Photo removed")).toBeVisible();
  await expect(photo(user)).toContainText("DU");
  await expect(photo(user).locator("img")).toHaveCount(0);
  await expect(user.getByRole("button", { name: "Upload a photo" })).toBeVisible();
  await expect(user.getByRole("button", { name: "Remove photo" })).toHaveCount(0);
  const answer = await user.evaluate(async (own) => {
    const response = await fetch(`/api/v1/users/${own}/avatar`);
    return { status: response.status, code: ((await response.json()) as { code: string }).code };
  }, userId);
  expect(answer).toEqual({ status: 404, code: "identity.avatar_not_found" });
  await expectNoToasts(user);
  await expectNoAccessibilityViolations(user);
});
