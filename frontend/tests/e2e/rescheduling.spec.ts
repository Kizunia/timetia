import { expect, test, type Page } from "@playwright/test";

import {
  ADMIN_USERNAME,
  adminPassword,
  DEMO_USERNAME,
  demoPassword,
  expectNoAccessibilityViolations,
  expectNoToasts,
  signIn,
} from "./support";

test.skip(!demoPassword || !adminPassword, "DEMO_USER_PASSWORD and DEMO_ADMIN_PASSWORD are not set; run 'pnpm bootstrap'");

/**
 * The main story of lecture rescheduling against the real stack with the college of `pnpm seed`: a teacher moves one of
 * their lectures and sends the ticket to the HOD, the HOD approves it, the timetable shows the move, the teacher is told,
 * and the teacher reverts it. It picks a lecture nobody has changed and puts it back, so it can run again on the same stack.
 */

async function get<T>(page: Page, path: string): Promise<T> {
  return page.evaluate(async (path) => {
    const response = await fetch(path, { headers: { "x-timetia-request": "1" } });
    if (!response.ok) throw new Error(`GET ${path}: ${response.status}`);
    return response.json();
  }, path) as Promise<T>;
}

interface Lecture {
  id: string;
  date: string;
  startTime: string;
  sessionType: string;
  cancelled: boolean;
  changed: boolean;
}

const isoIn = (days: number) => new Date(Date.now() + days * 86_400_000).toISOString().slice(0, 10);

/** A theory lecture of the signed-in teacher after today that nobody has touched. */
async function untouchedLecture(page: Page): Promise<Lecture | undefined> {
  const me = await get<{ faculty: { id: string }[] }>(page, "/api/v1/directory/me");
  if (!me.faculty[0]) return undefined;
  const live = await get<{ items: Lecture[] }>(
    page,
    `/api/v1/timetable/live?from=${isoIn(1)}&to=${isoIn(40)}&size=100&faculty=${me.faculty[0].id}`,
  );
  return live.items.find((l) => l.sessionType === "THEORY" && !l.cancelled && !l.changed);
}

async function openTicketAs(page: Page, id: string) {
  await page.goto(`/tickets/${id}`);
  await expect(page.getByRole("heading", { level: 1 })).toContainText("Reschedule");
}

test("a teacher moves a lecture, the HOD approves it, everyone is told, and it is put back", async ({ browser }) => {
  test.setTimeout(240_000);
  const teacherContext = await browser.newContext();
  const hodContext = await browser.newContext();
  const teacher = await teacherContext.newPage();
  const hod = await hodContext.newPage();

  await signIn(teacher, DEMO_USERNAME, demoPassword!);
  const lecture = await untouchedLecture(teacher);
  test.skip(!lecture, "There is no lecture to move: run 'pnpm seed' (and 'pnpm reset' first if the term is over)");

  let ticketId = "";

  await test.step("the teacher raises a reschedule to a free hour and sends it to the HOD", async () => {
    await teacher.goto(`/timetable?lecture=${lecture!.id}`);
    await teacher.getByRole("button", { name: "Change this lecture" }).click();
    await teacher.getByRole("menuitem", { name: "Reschedule this lecture" }).click();

    // Nothing in the seeded college is taught at 18:00.
    await teacher.getByRole("combobox", { name: "Starts" }).click();
    await teacher.getByRole("option", { name: "18:00" }).click();
    await expect(teacher.getByRole("combobox", { name: "Room" })).toBeEnabled();
    await teacher.getByRole("button", { name: "Create ticket" }).click();

    await teacher.waitForURL(/\/tickets\/[0-9a-f-]{36}$/);
    ticketId = teacher.url().split("/").pop()!;
    await expect(teacher.getByText("Draft", { exact: true })).toBeVisible();

    await teacher.getByRole("button", { name: "Send for review" }).click();
    const dialog = teacher.getByRole("dialog");
    await dialog.getByRole("group", { name: "HOD" }).getByRole("checkbox").first().click();
    await dialog.getByRole("button", { name: "Send for review" }).click();
    await expect(teacher.getByText("In review", { exact: true })).toBeVisible();
    await expectNoToasts(teacher);
    await expectNoAccessibilityViolations(teacher);
  });

  await test.step("the HOD finds it under To review and approves it", async () => {
    await signIn(hod, ADMIN_USERNAME, adminPassword!);
    await hod.goto("/tickets");
    await hod.getByRole("tab", { name: "To review" }).click();
    await hod.locator(`a[href="/tickets/${ticketId}"]`).click();
    await hod.getByRole("button", { name: "Review" }).click();
    await hod.getByRole("radio", { name: "Approve" }).click();
    await hod.getByRole("button", { name: "Send answer" }).click();
    await expect(hod.getByText("Applied", { exact: true })).toBeVisible();
  });

  await test.step("the timetable shows the move and the teacher is told", async () => {
    await teacher.goto(`/timetable?lecture=${lecture!.id}`);
    await expect(teacher.getByRole("dialog").getByText("Moved", { exact: true })).toBeVisible();
    await expect(teacher.getByRole("dialog").getByText("Moved from")).toBeVisible();

    await teacher.goto("/notifications");
    await expect(teacher.getByRole("banner").getByRole("link", { name: /Notifications, \d+\+? unread/ })).toBeVisible();
    await expect(teacher.getByRole("link", { name: "A reschedule was applied" }).first()).toBeVisible();
    await expectNoAccessibilityViolations(teacher);
  });

  await test.step("the teacher reverts it and the lecture is back", async () => {
    await openTicketAs(teacher, ticketId);
    await teacher.getByRole("button", { name: "Revert" }).click();
    await teacher.getByRole("button", { name: "Raise the revert" }).click();
    await teacher.waitForURL((url) => /\/tickets\/[0-9a-f-]{36}$/.test(url.pathname) && !url.pathname.endsWith(ticketId));
    await teacher.getByRole("button", { name: "Apply" }).click();
    await expect(teacher.getByText("Applied", { exact: true })).toBeVisible();

    const back = await get<Lecture>(teacher, `/api/v1/timetable/live/${lecture!.id}`);
    expect(back.startTime).toBe(lecture!.startTime);
    expect(back.date).toBe(lecture!.date);
  });

  await teacherContext.close();
  await hodContext.close();
});
