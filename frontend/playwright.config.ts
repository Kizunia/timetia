import { existsSync, readFileSync } from "node:fs";
import path from "node:path";

import { defineConfig, devices } from "@playwright/test";

// The demo user's password is generated into the root .env by 'pnpm bootstrap'. CI sets it in the environment.
const rootEnv = path.join(process.cwd(), "..", ".env");
if (!process.env.DEMO_USER_PASSWORD && existsSync(rootEnv)) {
  const match = /^DEMO_USER_PASSWORD=(.+)$/m.exec(readFileSync(rootEnv, "utf8"));
  if (match?.[1]) process.env.DEMO_USER_PASSWORD = match[1].trim();
}

/**
 * End-to-end tests run against an already running stack: `pnpm infra:up` and `pnpm dev`, or the `apps` Compose
 * profile. They do not start servers themselves, so the same tests cover both ways of running the applications.
 */
export default defineConfig({
  testDir: "tests/e2e",
  fullyParallel: false,
  workers: 1,
  retries: process.env.CI ? 1 : 0,
  reporter: process.env.CI ? [["github"], ["html", { open: "never" }]] : "list",
  use: {
    baseURL: process.env.E2E_BASE_URL ?? "http://localhost:3470",
    trace: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
});
