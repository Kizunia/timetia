import { expect, test } from "@playwright/test";

import { DEMO_USERNAME, demoPassword, signInOnKeycloak } from "./support";

test.skip(!demoPassword, "DEMO_USER_PASSWORD is not set; run 'pnpm bootstrap' or export it");

/**
 * Who a person is, to Timetia, is their email address: the application copies it from the token into the profile at every
 * request, and an import gives roles to the profile that holds an address. A person who could change their own address in
 * Keycloak could take that of someone about to be imported with a role, or the bootstrap address, and receive their roles.
 * So the realm lets only an administrator change the address and the username (infra/keycloak/base, user profile).
 *
 * This looks at Keycloak's own account page, where a person edits their details, as that person.
 */
test("a user cannot change their own email address or username in Keycloak's account console, only their name", async ({ page }) => {
  await page.goto("http://localhost:8180/realms/timetia/account/#/personal-info");
  await signInOnKeycloak(page, DEMO_USERNAME, demoPassword!);

  await expect(page.locator("#email")).toBeVisible({ timeout: 30_000 });
  await expect(page.locator("#email")).toBeDisabled();
  await expect(page.locator("#username")).toBeDisabled();
  // The invitation asks for a name, so a person keeps the right to give and correct theirs.
  await expect(page.locator("#firstName")).toBeEnabled();
  await expect(page.locator("#lastName")).toBeEnabled();
});
