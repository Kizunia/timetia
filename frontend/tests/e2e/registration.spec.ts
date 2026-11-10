import { expect, test } from "@playwright/test";

import { expectNoAccessibilityViolations, freshEmail, keycloakLink, mailTo, NEW_PASSWORD } from "./support";

/**
 * Self-registration on Keycloak's own page, then the rules for a user who has not verified their email address
 * (ADR 0041): they can see their own profile, ask for the email again and sign out, and nothing else, until they verify.
 * Keycloak sends the verification mail because the backend asks it to; Mailpit receives it.
 */
test("a user who registers is held to the unverified rules until they follow the link in the email", async ({ page, context }) => {
  const email = freshEmail("registered");

  await page.goto("/");
  await page.getByRole("link", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/localhost:8180\/realms\/kibootia\//);
  await page.getByRole("link", { name: "Register" }).click();
  // Keycloak's registration form: the ids are the stable part of its markup.
  await page.locator("#username").fill(email);
  await page.locator("#email").fill(email);
  await page.locator("#firstName").fill("Rita");
  await page.locator("#lastName").fill("Registered");
  await page.locator("#password").fill(NEW_PASSWORD);
  await page.locator("#password-confirm").fill(NEW_PASSWORD);
  await page.getByRole("button", { name: "Register" }).click();

  // Back in the application, signed in, with the notice that says what is missing.
  await expect(page.getByTestId("signed-in")).toBeVisible();
  const banner = page.getByTestId("unverified-banner");
  await expect(banner).toBeVisible();
  await expectNoAccessibilityViolations(page);

  // Their own profile works; what needs a verified address does not, and the screen says so.
  await page.goto("/profile");
  await expect(page.getByTestId("profile").locator('[data-field="email"]')).toContainText(email);
  await expect(page.getByTestId("profile").locator('[data-field="email"]')).toContainText("not verified");
  await expect(page.getByLabel("Colour scheme")).toBeDisabled();
  const refused = await page.evaluate(async () => {
    const response = await fetch("/api/v1/notes", { headers: { "x-kibootia-request": "1" } });
    return { status: response.status, code: ((await response.json()) as { code: string }).code };
  });
  expect(refused).toEqual({ status: 403, code: "platform.email_not_verified" });
  await page.goto("/admin");
  await expect(page.getByRole("heading", { name: "You do not have access to administration" })).toBeVisible();

  // The mail Keycloak sent because the backend asked it to; following the link verifies the address at once.
  const mail = await mailTo(page.request, email);
  const verification = await context.newPage();
  await verification.goto(keycloakLink(mail));
  // Keycloak asks for one more click before it marks the address verified.
  await verification.getByRole("link", { name: /Click here to proceed/ }).click();
  await verification.waitForLoadState("networkidle");
  await verification.close();

  // The token still says unverified until it is refreshed, so the second button does that and reloads.
  await page.goto("/profile");
  await expect(banner).toBeVisible();
  await banner.getByRole("button", { name: "I have verified" }).click();
  await expect(banner).toHaveCount(0);
  await expect(page.getByTestId("profile").locator('[data-field="email"]')).toContainText("verified");
  await expect(page.getByLabel("Colour scheme")).toBeEnabled();

  const allowed = await page.evaluate(async () => (await fetch("/api/v1/notes", { headers: { "x-kibootia-request": "1" } })).status);
  expect(allowed).toBe(200);
  await expectNoAccessibilityViolations(page);
});
