import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import { defineConfig, devices } from "@playwright/test";

// The demo users' passwords are generated into the root .env by 'pnpm bootstrap'. CI sets them in the environment.
const rootEnv = path.join(process.cwd(), "..", ".env");
for (const name of ["DEMO_USER_PASSWORD", "DEMO_ADMIN_PASSWORD"]) {
  if (!process.env[name] && existsSync(rootEnv)) {
    const match = new RegExp(`^${name}=(.+)$`, "m").exec(readFileSync(rootEnv, "utf8"));
    if (match?.[1]) process.env[name] = match[1].trim();
  }
}

/**
 * End-to-end tests run against an already running stack: `pnpm infra:up` and `pnpm dev`, or the `apps` Compose
 * profile. They do not start servers themselves, so the same tests cover both ways of running the applications.
 */
export default defineConfig({
  testDir: "tests/e2e",
  fullyParallel: false,
  workers: 1,
  // Keycloak's pages and the durable events behind the mail take a moment; a test does several round trips.
  timeout: 90_000,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3470",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
