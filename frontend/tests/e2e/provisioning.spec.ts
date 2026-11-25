import { expect, test, type BrowserContext, type Page } from "@playwright/test";

import {
  acceptInvitation,
  adminPassword,
  ADMIN_USERNAME,
  expectNoAccessibilityViolations,
  expectNoToasts,
  freshEmail,
  mailCountTo,
  mailTo,
  NEW_PASSWORD,
  signIn,
} from "./support";

test.skip(!adminPassword, "DEMO_ADMIN_PASSWORD is not set; run 'pnpm bootstrap' or export it");

/**
 * Provisioning against the real stack (Keycloak, Mailpit, MySQL, both applications), as the runbook describes it: the
 * Super Admin checks a file and imports it, the invited people accept and sign in, a repeat changes nothing, and the
 * roles that need a department, division or batch are refused because nothing provides those places yet (OQ-A15).
 * That refusal is part of what is verified here: the application must fail closed, not accept an unchecked place.
 *
 * The steps share the people and the stack, so they run in order.
 */
test.describe.configure({ mode: "serial" });

const superHod = freshEmail("superhod");
const faculty = freshEmail("faculty");
const hod = freshEmail("hod");
const file = `email,role,scope\n${superHod},Super HOD,\n${faculty},Faculty,\n${hod},HOD,CSE\n`;

let administrator: BrowserContext;
let admin: Page;

test.beforeAll(async ({ browser }) => {
  administrator = await browser.newContext();
  admin = await administrator.newPage();
});

test.afterAll(async () => {
  await administrator.close();
});

async function chooseFile(page: Page, csv: string) {
  await page.getByLabel("CSV file").setInputFiles({ name: "people.csv", mimeType: "text/csv", buffer: Buffer.from(csv) });
}

function row(page: Page, email: string) {
  return page.getByTestId("import-row").filter({ hasText: email });
}

test("the Super Admin checks the file: nothing is written, the places cannot be checked, and the page has no accessibility violations", async () => {
  await signIn(admin, ADMIN_USERNAME, adminPassword!);
  await expect(admin.getByTestId("signed-in")).toBeVisible();
  await admin.goto("/admin/import");
  await expect(admin.getByRole("heading", { name: "Import people", level: 1 })).toBeVisible();

  await chooseFile(admin, file);
  await admin.getByRole("button", { name: "Check the file" }).click();

  await expect(admin.getByTestId("import-summary")).toContainText("3 rows");
  await expect(admin.getByTestId("import-summary")).toContainText("2 new people");
  await expect(admin.getByTestId("import-summary")).toContainText("2 to add");
  await expect(admin.getByTestId("import-summary")).toContainText("1 with errors");
  await expect(row(admin, superHod)).toContainText("Would add");
  await expect(row(admin, faculty)).toContainText("Would add");
  // Fail closed: a department that cannot be checked is not accepted.
  await expect(row(admin, hod)).toContainText("Error");
  await expect(row(admin, hod)).toContainText("cannot be checked");
  await expectNoAccessibilityViolations(admin);

  // A check wrote nothing and sent nothing.
  expect(await mailCountTo(admin.request, superHod)).toBe(0);
  expect(await mailCountTo(admin.request, faculty)).toBe(0);
});

test("the import creates and invites the two people the application can place, and nobody for the HOD row", async () => {
  await admin.getByRole("button", { name: "Import", exact: true }).click();
  await expect(admin.getByText("Import finished")).toBeVisible();
  await expect(admin.getByTestId("import-summary")).toContainText("2 added");
  await expect(row(admin, superHod)).toContainText("Added");
  await expect(row(admin, superHod)).toContainText("invited");
  await expect(row(admin, hod)).toContainText("Error");
  await expectNoToasts(admin);
  await expectNoAccessibilityViolations(admin);

  // Keycloak mails the two invitations; the person whose row was refused was never created, so no mail goes to them.
  await mailTo(admin.request, superHod);
  await mailTo(admin.request, faculty);
  expect(await mailCountTo(admin.request, hod)).toBe(0);

  await admin.goto("/admin/users");
  await admin.getByLabel("Search").fill(hod);
  await admin.getByRole("button", { name: "Search" }).click();
  await expect(admin.getByTestId("user-row").filter({ hasText: hod })).toHaveCount(0);
});

test("the same file again changes nothing: the two roles are already there, and no second invitation is sent", async () => {
  await admin.goto("/admin/import");
  await chooseFile(admin, file);
  await admin.getByRole("button", { name: "Check the file" }).click();

  await expect(admin.getByTestId("import-summary")).toContainText("2 already there");
  await expect(admin.getByTestId("import-summary")).toContainText("0 new people");
  await expect(row(admin, superHod)).toContainText("Already there");
  await expect(row(admin, faculty)).toContainText("Already there");
  // Nothing would be added, so the Import button stays off.
  await expect(admin.getByRole("button", { name: "Import", exact: true })).toBeDisabled();
  expect(await mailCountTo(admin.request, superHod)).toBe(1);
  expect(await mailCountTo(admin.request, faculty)).toBe(1);
});

test("the Super Admin sees on each person's page the role the import gave them", async () => {
  for (const [email, role] of [
    [superHod, "Super HOD"],
    [faculty, "Faculty"],
  ] as const) {
    await admin.goto("/admin/users");
    await admin.getByLabel("Search").fill(email);
    await admin.getByRole("button", { name: "Search" }).click();
    await admin.getByTestId("user-row").filter({ hasText: email }).getByRole("link").click();
    await expect(admin.getByTestId("scoped-role-row")).toHaveCount(1);
    await expect(admin.getByTestId("scoped-role-row").filter({ hasText: role })).toBeVisible();
  }
});

test("the Super Admin cannot give HOD in a department on a person's page: the refusal is shown and nothing is added", async () => {
  await admin.goto("/admin/users");
  await admin.getByLabel("Search").fill(faculty);
  await admin.getByRole("button", { name: "Search" }).click();
  await admin.getByTestId("user-row").filter({ hasText: faculty }).getByRole("link").click();
  await expect(admin).toHaveURL(/\/admin\/users\/[0-9a-f-]{36}$/);

  await expect(admin.getByTestId("scoped-role-row").filter({ hasText: "Faculty" })).toBeVisible();
  await admin.getByLabel("Role", { exact: true }).selectOption({ label: "HOD" });
  await admin.getByLabel("Department code").fill("CSE");
  await admin.getByRole("button", { name: "Add role" }).click();

  await expect(admin.getByText("The role was not added")).toBeVisible();
  await expect(admin.getByText(/cannot be checked yet/)).toBeVisible();
  await expect(admin.getByTestId("scoped-role-row")).toHaveCount(1);
  await expectNoAccessibilityViolations(admin);
});

for (const [label, email] of [
  ["Super HOD", superHod],
  ["Faculty", faculty],
] as const) {
  test(`${label} accepts the invitation, signs in with the role they were given, sees no administration, and the API refuses the import`, async ({
    browser,
  }) => {
    const context = await browser.newContext();
    const page = await context.newPage();
    try {
      await acceptInvitation(page, page.request, email, label.replace(" ", ""), "Person");
      await signIn(page, email, NEW_PASSWORD, "/profile");
      await expect(page.getByRole("heading", { name: "Your profile" })).toBeVisible();
      // The profile lists platform roles only. A role in a place is not one: it never makes anyone an administrator, and
      // nothing in the running application shows a person their own roles in a place yet (the administrator sees them on
      // the person's page, checked above). What this person holds is shown by the administration area they do not get.
      const roles = page.getByTestId("profile").locator('[data-field="roles"]');
      await expect(roles).toContainText("User");
      await expect(roles).not.toContainText("Administrator");
      await expect(page.getByTestId("unverified-banner")).toHaveCount(0);

      // The role applies everywhere, but it is not an administrator and holds no administration permission.
      await expect(page.getByRole("link", { name: "Administration" })).toHaveCount(0);
      await page.goto("/admin/import");
      await expect(page.getByRole("heading", { name: "You do not have access to administration" })).toBeVisible();
      await expectNoAccessibilityViolations(page);

      // Hiding the link is for usability. Asked directly, the API refuses.
      const answers = await page.evaluate(async () => {
        const headers = { "x-timetia-request": "1" };
        const form = new FormData();
        form.append("file", new Blob(["email,role,scope\nx@y.test,Faculty,\n"], { type: "text/csv" }), "x.csv");
        const importing = await fetch("/api/v1/user-imports?dryRun=true", { method: "POST", headers, body: form });
        const assigning = await fetch("/api/v1/users/00000000-0000-0000-0000-000000000000/scoped-roles", {
          method: "POST",
          headers: { ...headers, "content-type": "application/json" },
          body: JSON.stringify({ roleId: "00000000-0000-0000-0000-000000000000" }),
        });
        return { importing: importing.status, assigning: assigning.status };
      });
      expect(answers).toEqual({ importing: 403, assigning: 403 });
    } finally {
      await context.close();
    }
  });
}
