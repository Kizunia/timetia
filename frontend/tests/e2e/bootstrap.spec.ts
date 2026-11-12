import { expect, test } from "@playwright/test";

import {
  acceptInvitation,
  adminPassword,
  ADMIN_USERNAME,
  expectNoAccessibilityViolations,
  mailCountTo,
  mailTo,
  NEW_PASSWORD,
  signIn,
} from "./support";

/**
 * The first Super Admin of a fresh environment, as the provisioning runbook describes it (ADR 0006, point 4): the operator
 * sets the address, the application invites it at startup, and the person becomes Super Admin when they first sign in with
 * the verified address. Nobody touches the database or the identity provider's console.
 *
 * This only makes sense on an environment that has never had an administrator, and with an address that is not the seeded
 * demo administrator. So it runs only when E2E_BOOTSTRAP_EMAIL names that address, which must also be the backend's
 * KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL (see "Verifying the bootstrap" in the testing guide).
 */
const bootstrapEmail = process.env.E2E_BOOTSTRAP_EMAIL;

test.skip(!bootstrapEmail, "E2E_BOOTSTRAP_EMAIL is not set; this needs a fresh environment (see the testing guide)");

test.describe.configure({ mode: "serial" });

test("the application invited the configured address at startup, and that person becomes Super Admin at first sign-in", async ({
  page,
}) => {
  const email = bootstrapEmail!;

  // The invitation went out when the backend started; nobody asked for it. (More than one if the backend was restarted
  // before the person accepted, which sends the link again.)
  await mailTo(page.request, email);
  expect(await mailCountTo(page.request, email)).toBeGreaterThanOrEqual(1);

  await acceptInvitation(page, page.request, email, "Ops", "Administrator");
  await signIn(page, email, NEW_PASSWORD);
  await expect(page.getByTestId("signed-in")).toBeVisible();

  // The first sign-in made them an administrator: the area is there, with the sections only an administrator has.
  await page.getByRole("link", { name: "Administration" }).click();
  await expect(page).toHaveURL(/\/admin\/users$/);
  await expect(page.getByRole("heading", { name: "Users" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Administration" }).getByRole("link", { name: "Import" })).toBeVisible();
  await expectNoAccessibilityViolations(page);

  // And it was recorded, with the person's id and no address.
  await page.goto("/admin/audit");
  await expect(page.getByTestId("audit-row").filter({ hasText: "identity.bootstrap-admin.granted" })).toHaveCount(1);
});

test("a second sign-in does not grant the role again", async ({ page }) => {
  await signIn(page, bootstrapEmail!, NEW_PASSWORD);
  await page.goto("/admin/audit");
  await expect(page.getByTestId("audit-row").filter({ hasText: "identity.bootstrap-admin.granted" })).toHaveCount(1);
});

test("someone else who signs in is not an administrator, whoever they are and whatever they were seeded as", async ({ page }) => {
  test.skip(!adminPassword, "DEMO_ADMIN_PASSWORD is not set");
  // The seeded demo administrator has a verified address, but it is not the configured one.
  await signIn(page, ADMIN_USERNAME, adminPassword!);
  await expect(page.getByTestId("signed-in")).toBeVisible();
  await expect(page.getByRole("link", { name: "Administration" })).toHaveCount(0);
  await page.goto("/admin/users");
  await expect(page.getByRole("heading", { name: "You do not have access to administration" })).toBeVisible();
});
