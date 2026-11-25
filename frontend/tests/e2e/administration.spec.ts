import { expect, test, type BrowserContext, type Page } from "@playwright/test";

import {
  adminPassword,
  ADMIN_USERNAME,
  expectNoAccessibilityViolations,
  freshEmail,
  keycloakLink,
  mailTo,
  NEW_PASSWORD,
  signIn,
} from "./support";

test.skip(!adminPassword, "DEMO_ADMIN_PASSWORD is not set; run 'pnpm bootstrap' or export it");

/**
 * One story, told in steps against the real stack (Keycloak, Mailpit, MySQL, both applications): the bootstrap
 * administrator invites someone, Keycloak mails the invitation, the invitee sets a password and signs in, and the
 * administrator disables, changes the roles of and finally erases them, watching the audit log. The steps share the
 * people and the stack, so they run in order.
 */
test.describe.configure({ mode: "serial" });

const roleName = `Reviewers ${Date.now().toString(36)}`;
const invitee = freshEmail("invitee");

let administrator: BrowserContext;
let admin: Page;
let inviteeContext: BrowserContext;
let guest: Page;

test.beforeAll(async ({ browser }) => {
  administrator = await browser.newContext();
  admin = await administrator.newPage();
  inviteeContext = await browser.newContext();
  guest = await inviteeContext.newPage();
});

test.afterAll(async () => {
  await administrator.close();
  await inviteeContext.close();
});

async function openUser(page: Page, email: string) {
  await page.goto("/admin/users");
  await page.getByLabel("Search").fill(email);
  await page.getByRole("button", { name: "Search" }).click();
  const row = page.getByTestId("user-row").filter({ hasText: email });
  await expect(row).toHaveCount(1);
  await row.getByRole("link").click();
  await expect(page).toHaveURL(/\/admin\/users\/[0-9a-f-]{36}$/);
}

async function confirm(page: Page, trigger: string, confirmLabel = trigger) {
  await page.getByRole("button", { name: trigger, exact: true }).click();
  await page.getByRole("alertdialog").getByRole("button", { name: confirmLabel, exact: true }).click();
}

test("the bootstrap administrator signs in and finds the administration area, which has no accessibility violations", async () => {
  await signIn(admin, ADMIN_USERNAME, adminPassword!);
  await expect(admin.getByTestId("signed-in")).toBeVisible();

  // The first sign-in with the bootstrap address made this user an administrator.
  await admin.getByRole("link", { name: "Administration" }).click();
  await expect(admin).toHaveURL(/\/admin\/users$/);
  await expect(admin.getByRole("heading", { name: "Users" })).toBeVisible();
  await expect(admin.getByTestId("user-row").filter({ hasText: "demo-admin@timetia.localhost" })).toBeVisible();
  await expectNoAccessibilityViolations(admin);

  for (const [link, heading] of [
    ["Roles", "Roles"],
    ["Audit log", "Audit log"],
  ] as const) {
    await admin.getByRole("navigation", { name: "Administration" }).getByRole("link", { name: link }).click();
    await expect(admin.getByRole("heading", { name: heading, level: 1 })).toBeVisible();
    await expect(admin.getByRole("status", { name: /loading/i })).toHaveCount(0);
    await expectNoAccessibilityViolations(admin);
  }
});

test("creates a role and cannot give it what the administrator does not hold", async () => {
  await admin.goto("/admin/roles/new");
  await admin.getByLabel("Name").fill(roleName);
  await admin.getByLabel("Description").fill("Looks at things");
  await admin.getByRole("checkbox", { name: /View roles/ }).check();
  await admin.getByRole("button", { name: "Create role" }).click();

  await expect(admin).toHaveURL(/\/admin\/roles\/[0-9a-f-]{36}$/);
  await expect(admin.getByRole("heading", { name: roleName })).toBeVisible();
  await expect(admin.getByRole("checkbox", { name: /View roles/ })).toBeChecked();
  await expectNoAccessibilityViolations(admin);

  await admin.goto("/admin/roles/new");
  await admin.getByLabel("Name").fill(roleName);
  await admin.getByRole("button", { name: "Create role" }).click();
  await expect(admin.getByText("Another role already has this name.")).toBeVisible();
});

test("invites a user with that role, and Keycloak mails the invitation", async () => {
  await admin.goto("/admin/users");
  await admin.getByRole("button", { name: "Invite user" }).click();
  await admin.getByLabel("Email address").fill(invitee);
  await admin.getByRole("checkbox", { name: roleName }).check();
  await admin.getByRole("button", { name: "Send invitation" }).click();
  await expect(admin.getByText(`Invitation sent to ${invitee}`)).toBeVisible();

  await openUser(admin, invitee);
  await expect(admin.getByRole("heading", { name: "Unnamed user" })).toBeVisible();
  await expect(admin.getByText("Invited", { exact: true })).toBeVisible();
  await expect(admin.getByRole("checkbox", { name: roleName })).toBeChecked();

  const mail = await mailTo(admin.request, invitee);
  expect(mail.subject.length).toBeGreaterThan(0);
  expect(keycloakLink(mail)).toContain("/realms/timetia/");
});

test("the invitee follows the link, sets a password and signs in as a user with both roles", async () => {
  const link = keycloakLink(await mailTo(guest.request, invitee));
  await guest.goto(link);

  // Keycloak asks for a new password and then for a name; the order and the confirmation page are Keycloak's.
  for (let step = 0; step < 5; step++) {
    const proceed = guest.getByRole("link", { name: /Click here to proceed/ });
    if (await proceed.isVisible()) {
      await proceed.click();
    } else if (await guest.locator("#password-new").isVisible()) {
      await guest.locator("#password-new").fill(NEW_PASSWORD);
      await guest.locator("#password-confirm").fill(NEW_PASSWORD);
      await guest.locator("input[type=submit], button[type=submit]").first().click();
    } else if (await guest.locator("#firstName").isVisible()) {
      await guest.locator("#firstName").fill("Ivy");
      await guest.locator("#lastName").fill("Invitee");
      await guest.locator("input[type=submit], button[type=submit]").first().click();
    } else {
      break;
    }
    await guest.waitForLoadState("networkidle");
  }

  await signIn(guest, invitee, NEW_PASSWORD, "/profile");
  await expect(guest.getByRole("heading", { name: "Your profile" })).toBeVisible();
  await expect(guest.getByTestId("profile").locator('[data-field="name"]')).toHaveText("Ivy Invitee");
  await expect(guest.getByTestId("profile").locator('[data-field="roles"]')).toContainText("User");
  await expect(guest.getByTestId("profile").locator('[data-field="roles"]')).toContainText(roleName);
  await expect(guest.getByTestId("unverified-banner")).toHaveCount(0);
  await expectNoAccessibilityViolations(guest);

  // They hold View roles, so the Roles section shows, and Users does not.
  await guest.goto("/admin");
  await expect(guest.getByRole("navigation", { name: "Administration" }).getByRole("link")).toHaveText(["Roles"]);
  await expect(guest.getByRole("heading", { name: "Roles", level: 1 })).toBeVisible();
});

test("disabling a user refuses them on their very next request, and enabling lets them back", async () => {
  await openUser(admin, invitee);
  await confirm(admin, "Disable");
  await expect(admin.getByText("User disabled")).toBeVisible();

  await guest.goto("/profile");
  await expect(guest).toHaveURL(/\/account-disabled$/);
  await expect(guest.getByRole("heading", { name: "Your account is disabled" })).toBeVisible();

  await confirm(admin, "Enable");
  await expect(admin.getByText("User enabled")).toBeVisible();
  await guest.goto("/profile");
  await expect(guest.getByRole("heading", { name: "Your profile" })).toBeVisible();
});

test("taking a role away applies on the user's next request", async () => {
  await openUser(admin, invitee);
  await admin.getByRole("checkbox", { name: roleName }).uncheck();
  await admin.getByRole("button", { name: "Save roles" }).click();
  await expect(admin.getByText("Roles saved")).toBeVisible();

  await guest.goto("/admin");
  await expect(guest.getByRole("heading", { name: "You do not have access to administration" })).toBeVisible();
  await guest.goto("/profile");
  await expect(guest.getByTestId("profile").locator('[data-field="roles"]')).not.toContainText(roleName);
});

test("the last administrator can neither disable themselves nor lose the administrator role", async () => {
  await openUser(admin, "demo-admin@timetia.localhost");
  await expect(admin.getByRole("button", { name: "Disable", exact: true })).toHaveCount(0);
  await expect(admin.getByRole("button", { name: "Delete", exact: true })).toHaveCount(0);

  await admin.getByRole("checkbox", { name: /Administrator/ }).uncheck();
  await admin.getByRole("button", { name: "Save roles" }).click();
  await expect(admin.getByRole("alert").filter({ hasText: "The roles were not saved" })).toContainText(
    "last administrator who can sign in",
  );

  // The form still shows what was unticked, next to the error; what the server holds is unchanged.
  await admin.reload();
  await expect(admin.getByRole("checkbox", { name: /Administrator/ })).toBeChecked();
});

test("deleting a user, restoring them, and erasing them for good leaves only their ID in the log", async () => {
  await openUser(admin, invitee);
  await confirm(admin, "Delete");
  await expect(admin.getByText("User deleted")).toBeVisible();
  await expect(admin.getByText("Deleted", { exact: true })).toBeVisible();

  await guest.goto("/profile");
  await expect(guest).toHaveURL(/\/account-disabled$/);

  await confirm(admin, "Restore");
  await expect(admin.getByText("User restored")).toBeVisible();
  await confirm(admin, "Delete");
  await expect(admin.getByText("User deleted")).toBeVisible();

  await confirm(admin, "Erase for good");
  await expect(admin.getByText("Erasing the user has begun")).toBeVisible();

  // The removal runs afterwards, from a durable event, so the user leaves the list of deleted users a moment later. The
  // signed-in browser context asks the API, which is quicker and steadier to wait on than a screen that is still loading.
  await expect
    .poll(
      async () => {
        const response = await admin.request.get("/api/v1/users", { params: { state: "DELETED", q: invitee } });
        return ((await response.json()) as { page: { totalElements: number } }).page.totalElements;
      },
      { timeout: 30_000, message: "the erased user to leave the list of deleted users" },
    )
    .toBe(0);

  await admin.goto("/admin/audit");
  await expect(admin.getByTestId("audit-row").first()).toBeVisible();
  const log = await admin.getByTestId("audit-row").allTextContents();
  expect(log.join("\n")).toContain("identity.user.invited");
  expect(log.join("\n")).toContain("Deleted user");
  expect(log.join("\n")).not.toContain(invitee);
  await expectNoAccessibilityViolations(admin);
});
