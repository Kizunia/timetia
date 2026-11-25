import { expect, test } from "@playwright/test";

/**
 * Nobody can create their own account (Timetia ADR 0005): people arrive by invitation, and later by import. Keycloak's
 * sign-in page offers no registration link, and the registration address itself is refused.
 *
 * The rules for a user who has not verified their email address (ADR 0041) are still tested in the backend, with tokens
 * that say so (IdentityGateIT, AccessApiIT, AvatarApiIT). They are not reachable from the browser any more, because the
 * only way to get an unverified account was to register.
 */
test("the sign-in page offers no registration and the registration address is refused", async ({ page }) => {
  await page.goto("/");
  await page.getByRole("link", { name: "Sign in" }).click();
  await expect(page).toHaveURL(/localhost:8180\/realms\/timetia\//);
  await expect(page.locator("#kc-login")).toBeVisible();
  await expect(page.getByRole("link", { name: "Register" })).toHaveCount(0);

  // Keycloak's registration address for this realm, built from the sign-in page's own address. With registration off
  // it shows an error page and never a form with a password field.
  const registration = page.url().replace("/protocol/openid-connect/auth", "/protocol/openid-connect/registrations");
  const response = await page.goto(registration);
  expect(response?.status()).toBeGreaterThanOrEqual(400);
  await expect(page.locator("#password-confirm")).toHaveCount(0);
});
