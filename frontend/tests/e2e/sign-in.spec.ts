import AxeBuilder from "@axe-core/playwright";
import { expect, test, type Page } from "@playwright/test";

const DEMO_USERNAME = "demo";
const demoPassword = process.env.DEMO_USER_PASSWORD;

test.skip(!demoPassword, "DEMO_USER_PASSWORD is not set; run 'pnpm bootstrap' or export it");

async function signIn(page: Page, returnPath = "/") {
  await page.goto(returnPath === "/" ? "/" : returnPath);
  if (returnPath === "/") await page.getByRole("link", { name: "Sign in" }).click();
  // Keycloak's own login page, on its public address.
  await expect(page).toHaveURL(/localhost:8180\/realms\/kibootia\//);
  await page.locator("#username").fill(DEMO_USERNAME);
  await page.locator("#password").fill(demoPassword!);
  await page.locator("#kc-login").click();
}

test.describe("signing in through the Next.js server", () => {
  test("signs in, shows the identity from Spring both server-side and through the proxy, and signs out", async ({ page, context }) => {
    await signIn(page);
    await expect(page.getByTestId("signed-in")).toBeVisible();

    await page.getByRole("link", { name: /who the API thinks you are/i }).click();
    await expect(page).toHaveURL(/\/me$/);

    // Rendered by the server from a direct call to Spring.
    const server = page.getByTestId("me-server");
    await expect(server.locator('[data-field="username"]')).toHaveText("demo");
    await expect(server.locator('[data-field="email"]')).toHaveText("demo@kibootia.localhost");
    await expect(server.locator('[data-field="emailVerified"]')).toHaveText("yes");

    // Fetched by the browser through /api/v1/me, with the token added by the server.
    const browser = page.getByTestId("me-browser");
    await expect(browser.locator('[data-field="username"]')).toHaveText("demo");
    await expect(browser.locator('[data-field="id"]')).toHaveText(await server.locator('[data-field="id"]').innerText());

    // The browser never holds a token: the session lives in HttpOnly cookies only.
    const cookies = (await context.cookies()).filter((cookie) => cookie.name.startsWith("kibootia_session"));
    expect(cookies.length).toBeGreaterThan(0);
    for (const cookie of cookies) {
      expect(cookie.httpOnly).toBe(true);
      expect(cookie.sameSite).toBe("Lax");
    }
    expect(await page.evaluate(() => document.cookie)).not.toContain("kibootia_session");
    expect(await page.content()).not.toMatch(/eyJ[A-Za-z0-9_-]{10,}\./);

    await page.getByRole("button", { name: "Sign out" }).click();
    await expect(page.getByTestId("signed-out")).toBeVisible();

    // The Keycloak session ended too: visiting a protected page asks for credentials again.
    await page.goto("/me");
    await expect(page).toHaveURL(/localhost:8180\/realms\/kibootia\//);
  });

  test("sends a signed-out visitor to sign in and back to the page they asked for", async ({ page }) => {
    await page.goto("/me");
    await expect(page).toHaveURL(/localhost:8180\/realms\/kibootia\//);
    await page.locator("#username").fill(DEMO_USERNAME);
    await page.locator("#password").fill(demoPassword!);
    await page.locator("#kc-login").click();
    await expect(page).toHaveURL(/\/me$/);
    await expect(page.getByTestId("me-server")).toBeVisible();
  });

  test("the proxy refuses a state-changing request without the Origin and custom header, and an unauthenticated one", async ({ page, browser }) => {
    await signIn(page);
    await expect(page.getByTestId("signed-in")).toBeVisible();

    // From the page itself the Origin is right but the custom header is missing.
    const missingHeader = await page.evaluate(async () => (await fetch("/api/v1/me", { method: "POST" })).status);
    expect(missingHeader).toBe(403);

    // A visitor with no session gets 401, not a redirect, and no cookies are set.
    const anonymous = await browser.newContext();
    const response = await anonymous.request.get("/api/v1/me", { headers: { Accept: "application/json" } });
    expect(response.status()).toBe(401);
    expect((await response.json()).code).toBe("bff.unauthenticated");
    await anonymous.close();
  });

  test("has no automatically detectable accessibility violations on the pages the user sees", async ({ page }) => {
    await page.goto("/");
    await expect(page.getByTestId("signed-out")).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);

    await signIn(page);
    await page.goto("/me");
    await expect(page.getByTestId("me-browser")).toBeVisible();
    expect((await new AxeBuilder({ page }).analyze()).violations).toEqual([]);
  });
});
