// One command that makes the whole demo: `pnpm seed`. Needs the stack running (`pnpm infra:up`, `pnpm dev`).
//  1. creates the sign-in accounts in Keycloak (teachers, an HOD, a CR, students) through its admin API;
//  2. signs in as each once in a headless browser (that creates their Timetia profile);
//  3. signs in as demo-admin and runs scripts/seed-college.browser.js (campus, timetables, roles, 50 invited students);
//  4. adds sample activity: tickets from Dr Demo (one applied, one waiting for the HOD, one draft) and an issue from the CR.
// Safe to run again: accounts that exist are kept and the campus seed refuses to run twice.
import { existsSync, readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

const root = join(dirname(fileURLToPath(import.meta.url)), "..");
const env = Object.fromEntries(
  readFileSync(join(root, ".env"), "utf8").split(/\r?\n/).filter((l) => l && !l.startsWith("#") && l.includes("="))
    .map((l) => [l.slice(0, l.indexOf("=")), l.slice(l.indexOf("=") + 1)]),
);
const KEYCLOAK = process.env.KEYCLOAK_URL ?? "http://localhost:8180";
const WEB = process.env.WEB_URL ?? "http://localhost:3470";
const REALM = "timetia";
const PASSWORD = env.DEMO_USER_PASSWORD;
const { chromium } = createRequire(join(root, "frontend", "package.json"))("@playwright/test");

/** username, first name, last name */
const ACCOUNTS = [
  ["rao", "Anita", "Rao"], ["mehta", "Vikram", "Mehta"], ["iyer", "Lakshmi", "Iyer"], ["khan", "Imran", "Khan"],
  ["hod.me", "Meera", "Shah"], ["cr.cse-a", "Chirag", "Patil"], ["stu.cse-a1", "Sneha", "Kulkarni"], ["stu.me-a2", "Rohan", "Desai"],
];

async function step(name, work) {
  process.stdout.write(`${name} ... `);
  try { const result = await work(); console.log("ok"); return result; }
  catch (error) { console.log("FAILED"); throw error; }
}

async function keycloakAdminToken() {
  const response = await fetch(`${KEYCLOAK}/realms/master/protocol/openid-connect/token`, {
    method: "POST",
    headers: { "content-type": "application/x-www-form-urlencoded" },
    body: new URLSearchParams({ grant_type: "password", client_id: "admin-cli", username: "admin", password: env.KEYCLOAK_ADMIN_PASSWORD }),
  });
  if (!response.ok) throw new Error(`Keycloak admin sign-in failed (${response.status}). Is the stack up?`);
  return (await response.json()).access_token;
}

async function createAccounts() {
  const token = await keycloakAdminToken();
  for (const [username, firstName, lastName] of ACCOUNTS) {
    const response = await fetch(`${KEYCLOAK}/admin/realms/${REALM}/users`, {
      method: "POST",
      headers: { authorization: `Bearer ${token}`, "content-type": "application/json" },
      body: JSON.stringify({
        username, email: `${username}@college.test`, firstName, lastName, enabled: true, emailVerified: true,
        credentials: [{ type: "password", value: PASSWORD, temporary: false }],
      }),
    });
    if (!response.ok && response.status !== 409) throw new Error(`Creating ${username} failed: ${response.status} ${await response.text()}`);
  }
}

async function signIn(browser, username, password) {
  const context = await browser.newContext();
  const page = await context.newPage();
  await page.goto(WEB);
  if (!/8180/.test(page.url())) await page.getByRole("link", { name: "Sign in" }).click();
  await page.locator("#username").fill(username);
  await page.locator("#password").fill(password);
  await page.locator("#kc-login").click();
  await Promise.race([page.getByTestId("signed-in").waitFor({ timeout: 60000 }), page.waitForURL(/\/timetable/, { timeout: 60000 })]);
  return { context, page };
}

/** Runs in the page of a signed-in user: sample tickets of Dr Demo. */
async function demoActivity() {
  const h = { "content-type": "application/json", "x-timetia-request": "1" };
  const call = async (m, p, b) => {
    const r = await fetch(p, { method: m, headers: h, body: b === undefined ? undefined : JSON.stringify(b) });
    const t = await r.text();
    if (!r.ok) throw new Error(`${m} ${p} ${r.status} ${t}`);
    return t ? JSON.parse(t) : null;
  };
  if ((await call("GET", "/api/v1/tickets?mine=true&size=1")).items.length > 0) return;   // already done
  const iso = (d) => d.toISOString().slice(0, 10);
  const from = new Date(); const to = new Date(Date.now() + 40 * 864e5);
  const lectures = (await call("GET", `/api/v1/timetable/live?from=${iso(from)}&to=${iso(to)}&size=100&faculty=${(await call("GET", "/api/v1/directory/me")).faculty[0].id}`)).items;
  const now = new Date();
  const future = lectures.filter((l) => l.sessionType === "THEORY" && new Date(`${l.date}T${l.startTime}`) > now);
  const mondays = future.filter((l) => l.date === future[0].date);
  const day = (d, n) => { const x = new Date(d + "T12:00:00Z"); x.setUTCDate(x.getUTCDate() + n); return iso(x); };
  const slot = (l, dayOffset, hour) => ({ date: day(l.date, dayOffset), startTime: `${String(hour).padStart(2, "0")}:00`, endTime: `${String(hour + 1).padStart(2, "0")}:00`, mode: "IN_PERSON", roomId: l.roomId });
  const [a, b, c] = mondays;
  const done = await call("POST", "/api/v1/tickets", { type: "RESCHEDULE", lectureId: a.id, description: "Seminar clash, moving to Wednesday afternoon", target: slot(a, 2, 14) });
  await call("POST", `/api/v1/tickets/${done.id}/apply`);
  if (b) {
    const admin = (await call("GET", "/api/v1/users?size=100")).items.find((u) => u.username === "demo-admin");
    const waiting = await call("POST", "/api/v1/tickets", { type: "RESCHEDULE", lectureId: b.id, description: "Lab slot needed", target: slot(b, 2, 15) });
    await call("POST", `/api/v1/tickets/${waiting.id}/submit`, { reviewers: [{ userId: admin.id, capacity: "HOD" }] });
  }
  if (c) await call("POST", "/api/v1/tickets", { type: "CANCEL", lectureId: c.id, reason: "Away at a conference" });
}

/** Runs in the page of the CR: an issue about a theory lecture that mentions Dr Demo. */
async function crActivity() {
  const h = { "content-type": "application/json", "x-timetia-request": "1" };
  const call = async (m, p, b) => {
    const r = await fetch(p, { method: m, headers: h, body: b === undefined ? undefined : JSON.stringify(b) });
    const t = await r.text();
    if (!r.ok) throw new Error(`${m} ${p} ${r.status} ${t}`);
    return t ? JSON.parse(t) : null;
  };
  if ((await call("GET", "/api/v1/issues?size=1")).items.length > 0) return;   // already done
  const division = (await call("GET", "/api/v1/directory/divisions")).find((d) => d.code === "CSE-A");
  const demo = (await call("GET", "/api/v1/directory/faculties")).find((f) => f.code === "DEMO");
  await call("POST", "/api/v1/issues", {
    sessionType: "THEORY", divisionId: division.id, title: "Please add an extra OOP doubt-solving lecture",
    description: "The whole class struggles with inheritance before the test.", mentionedFacultyIds: [demo.id],
  });
}

if (!existsSync(join(root, ".env"))) throw new Error("No .env. Run `pnpm bootstrap` first.");
await step("Creating sign-in accounts in Keycloak", createAccounts);
const browser = await chromium.launch();
try {
  for (const [username] of [["demo"], ...ACCOUNTS]) {
    await step(`First sign-in of ${username}`, async () => {
      const { context } = await signIn(browser, username, PASSWORD);
      await context.close();
    });
  }
  const admin = await signIn(browser, "demo-admin", env.DEMO_ADMIN_PASSWORD);
  await step("Seeding the college (about a minute)", async () => {
    const failures = [];
    admin.page.on("console", (m) => { if (m.type() === "error" || m.type() === "warning") failures.push(m.text()); });
    await admin.page.evaluate(readFileSync(join(root, "scripts", "seed-college.browser.js"), "utf8"));
    const bad = failures.filter((f) => !/favicon/.test(f));
    if (bad.length) console.log("\n  notes: " + bad.join("\n  "));
  });
  await admin.context.close();
  const demo = await signIn(browser, "demo", PASSWORD);
  await step("Sample tickets of Dr Demo", () => demo.page.evaluate(demoActivity));
  await demo.context.close();
  const cr = await signIn(browser, "cr.cse-a", PASSWORD);
  await step("Sample issue of the CR", () => cr.page.evaluate(crActivity));
  await cr.context.close();
} finally {
  await browser.close();
}
console.log(`\nDone. Open ${WEB}. Accounts and passwords: scripts/PRESENT.md section 3.`);
