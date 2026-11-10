import AxeBuilder from "@axe-core/playwright";
import { expect, type APIRequestContext, type Page } from "@playwright/test";

/** Where the mail catcher of the local stack listens. Keycloak sends every identity email there (ADR 0041). */
const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";

export const ADMIN_USERNAME = "demo-admin";
export const adminPassword = process.env.DEMO_ADMIN_PASSWORD;
export const DEMO_USERNAME = "demo";
export const demoPassword = process.env.DEMO_USER_PASSWORD;

/** A password that satisfies the realm's policy (12 characters, not the username or email). */
export const NEW_PASSWORD = "Correct-horse-battery-1";

let counter = 0;

/** An address nobody has used, so tests can run again against the same stack. */
export function freshEmail(prefix: string): string {
  counter += 1;
  return `${prefix}.${Date.now().toString(36)}${counter}@kibootia.test`;
}

/** Fills Keycloak's own login page, which both the application and the tests reach on its public address. */
export async function signInOnKeycloak(page: Page, username: string, password: string) {
  await expect(page).toHaveURL(/localhost:8180\/realms\/kibootia\//);
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
}

/** Starts at the home page, follows Sign in, and signs in as the user. */
export async function signIn(page: Page, username: string, password: string, path = "/") {
  await page.goto(path);
  if (!/localhost:8180/.test(page.url())) await page.getByRole("link", { name: "Sign in" }).click();
  await signInOnKeycloak(page, username, password);
}

export async function signOut(page: Page) {
  await page.getByRole("button", { name: "Sign out" }).click();
  await expect(page.getByTestId("signed-out")).toBeVisible();
}

export async function expectNoAccessibilityViolations(page: Page) {
  const results = await new AxeBuilder({ page }).analyze();
  // Name the elements, so a failure says what to fix and not only that something is wrong.
  expect(
    results.violations.map(
      (violation) => `${violation.id}: ${violation.help}: ${violation.nodes.map((node) => node.target.join(" ")).join(" | ")}`,
    ),
  ).toEqual([]);
}

export type ImageType = "image/jpeg" | "image/png" | "image/webp";

/** A real image of the type, made by the browser itself: a coloured field with a white corner, so it is not a blank. */
export async function makeImage(page: Page, type: ImageType, width: number, height: number, colour: string): Promise<Buffer> {
  const bytes = await page.evaluate(
    async ({ type, width, height, colour }) => {
      const canvas = document.createElement("canvas");
      canvas.width = width;
      canvas.height = height;
      const context = canvas.getContext("2d")!;
      context.fillStyle = colour;
      context.fillRect(0, 0, width, height);
      context.fillStyle = "#ffffff";
      context.fillRect(0, 0, width / 4, height / 4);
      const blob = await new Promise<Blob | null>((resolve) => canvas.toBlob(resolve, type, 0.9));
      if (!blob || blob.type !== type) throw new Error(`This browser cannot encode ${type}`);
      return Array.from(new Uint8Array(await blob.arrayBuffer()));
    },
    { type, width, height, colour },
  );
  return Buffer.from(bytes);
}

/**
 * Axe measures colours as they are at that moment, and a toast that is still fading in or out has a colour that is not
 * its final one, so a check made while one is on screen fails now and then. Wait until they are gone.
 */
export async function expectNoToasts(page: Page) {
  await expect(page.locator("[data-sonner-toast]")).toHaveCount(0, { timeout: 15_000 });
}

interface Mail {
  subject: string;
  text: string;
}

/** Waits for a mail to the address and returns it. Mailpit holds every message until it is deleted. */
export async function mailTo(request: APIRequestContext, address: string, subjectContains?: string): Promise<Mail> {
  let found: Mail | undefined;
  await expect
    .poll(
      async () => {
        const search = await request.get(`${MAILPIT}/api/v1/search`, { params: { query: `to:${address}` } });
        if (!search.ok()) return false;
        const { messages } = (await search.json()) as { messages: { ID: string; Subject: string }[] };
        const match = messages.find((message) => !subjectContains || message.Subject.includes(subjectContains));
        if (!match) return false;
        const detail = await request.get(`${MAILPIT}/api/v1/message/${match.ID}`);
        found = { subject: match.Subject, text: ((await detail.json()) as { Text: string }).Text };
        return true;
      },
      { message: `a mail to ${address}`, timeout: 30_000, intervals: [500, 1000] },
    )
    .toBe(true);
  return found!;
}

/** The first link in a mail that goes to Keycloak. */
export function keycloakLink(mail: Mail): string {
  const link = /https?:\/\/localhost:8180\/[^\s<>"]+/.exec(mail.text)?.[0];
  if (!link) throw new Error(`There is no Keycloak link in the mail "${mail.subject}":\n${mail.text}`);
  return link;
}
