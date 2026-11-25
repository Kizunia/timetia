# Handover: Phase 3 (issues, notifications, setup, finish)

Branch `dog`. Written 2026-11-25. Spec: `frontend/docs/phase-3.md`. Nothing is committed.

## Done

- **Backend**: names on issues; notifications say who, what kind, which lecture and whether the person may open the ticket;
  `PUT /faculties/{id}/account`; `reference.AcademicDirectory.batchCodes`, `rescheduling.TicketSummaries`.
- **Frontend**: notifications and issues finished (tabs, mention, link ticket, close rules); new Campus and Timetables
  screens under Administration; component tests for tickets, issues, notifications and setup; one end-to-end test.
- **Docs**: `scripts/DEMO.md` is a click-through; deferred features are in the module's `open-questions.md`.

## Bugs found and fixed

- A student's notification led to "not found". Fixed.
- The rule in `domain.md` for that was also wrong: a batch representative who is also a student was still sent to a theory
  ticket they cannot open (404). The server now says (`ticketVisible`), and `domain.md` is updated.
- Administration pages scrolled sideways on a phone.
- The whole app was drawn in a serif font (a CSS variable that referred to itself).

## Different from the spec

- The demo accounts are those of `pnpm seed` (`stu.cse-a1`, `cr.cse-a`, ...); `demo-cr`, `demo-br`, `demo-student` were not added.
- The 15 tests recorded as failing before Timetia now pass without any change.

## What was run

- Backend full `verify`: 250 unit + 594 integration tests, no failures. The `ticketVisible` change came **after** this run;
  for it only `NotificationApiIT`, `OpenApiContractIT` (13 tests), `ArchitectureTest` and `ModularityTest` (14) were run.
- Frontend: typecheck, lint (0 errors, 2 older warnings), 353 unit tests, production build.
- Live stack: `pnpm seed`; `tests/e2e/rescheduling.spec.ts`; a one-off scripted walk of campus create/rename/delete, a draft
  timetable with a clash, the issue flow, student notification links, and 400 px width. The end-to-end test and the walk ran
  against the production web server (`next build` + `next start`).

## Checked later the same day (scripted browser against `pnpm dev`)

All passed: cancel, substitute, exchange, permanent change, extra lecture, reject then edit, escalate, apply and revert,
a blocked clashing move, an HOD acting for a teacher, a student opening a ticket page, the ticket page and raise dialog at
400 px, publishing a timetable, and removing and restoring a faculty account link. In a real Chrome the signed-out home page
did not reload itself under `pnpm dev`.

## Not done or not checked

- Parts of the Phase 3 exit check in a browser: the audit log entries, the HOD's issue list, what a class representative and
  a batch representative may not do or see, and newly published lectures appearing (the test publish created none). Backend
  tests cover the visibility rules.
- The older Playwright specs (sign-in, identity, notes, ...) were not run.
- `pnpm dev` once reloaded the home page endlessly, so `pnpm seed` failed. It has not happened on later starts and the cause
  is unknown. If it does, restart `pnpm dev`; `pnpm apps:up` runs the apps without live reload.
- Deferred features: `timetia-docs/modules/lecture-rescheduling/open-questions.md`.

## Local data

- Test runs added tickets (applied changes were reverted, drafts withdrawn) and two closed issues named "Walkthrough ...".
- A cancelled extra OOP lecture sits on Saturday 12 December, 18:00.
- ME-B has an extra published version from Sunday 13 December. It creates no lectures.
- The seeded term runs from 16 November to 13 December. To present after that, `pnpm reset` (deletes all local data) and
  seed again, which also clears the items above.
