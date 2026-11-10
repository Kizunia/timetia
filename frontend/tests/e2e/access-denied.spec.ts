import { expect, test } from "@playwright/test";

import { DEMO_USERNAME, demoPassword, expectNoAccessibilityViolations, signIn } from "./support";

test.skip(!demoPassword, "DEMO_USER_PASSWORD is not set; run 'pnpm bootstrap' or export it");

/**
 * The denied side of administration (verification standard): an ordinary signed-in user is shown nothing of it and, if
 * they ask the API directly, is refused. Hiding a link is for usability; the 403 is the control.
 */
test("an ordinary user sees no administration, is told so, and the API refuses them", async ({ page }) => {
  await signIn(page, DEMO_USERNAME, demoPassword!);
  await expect(page.getByTestId("signed-in")).toBeVisible();
  await expect(page.getByRole("link", { name: "Administration" })).toHaveCount(0);

  await page.goto("/admin/users");
  await expect(page.getByRole("heading", { name: "You do not have access to administration" })).toBeVisible();
  await expect(page.getByRole("navigation", { name: "Administration" })).toHaveCount(0);
  await expectNoAccessibilityViolations(page);

  // Asked directly, with the right CSRF header, the API says no, and says it is a permission problem.
  const answers = await page.evaluate(async () => {
    const headers = { "x-kibootia-request": "1", "content-type": "application/json" };
    const call = async (method: string, path: string, body?: string) => {
      const response = await fetch(path, { method, headers, body });
      return { status: response.status, code: ((await response.json()) as { code: string }).code };
    };
    return {
      users: await call("GET", "/api/v1/users"),
      roles: await call("GET", "/api/v1/roles"),
      audit: await call("GET", "/api/v1/audit-entries"),
      invite: await call("POST", "/api/v1/users", JSON.stringify({ email: "nobody@kibootia.test" })),
    };
  });
  for (const answer of Object.values(answers)) {
    expect(answer).toEqual({ status: 403, code: "platform.forbidden" });
  }

  // What a user may do with their own account works without any permission.
  await page.goto("/profile");
  await expect(page.getByRole("heading", { name: "Your profile" })).toBeVisible();
  await expect(page.getByTestId("profile").locator('[data-field="roles"]')).toContainText("User");
});
