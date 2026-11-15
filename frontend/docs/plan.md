# Plan: three phases to a finished course project

This file is the overview. The implementation spec of each phase is in [phase-1.md](phase-1.md),
[phase-2.md](phase-2.md) and [phase-3.md](phase-3.md); read [domain.md](domain.md), [api-reference.md](api-reference.md),
[architecture.md](architecture.md) and [backend-guide.md](backend-guide.md) first.

## 1. Goal and definition of "complete"

The course project is the **lecture-rescheduling module of Timetia** (`timetia-docs/modules/lecture-rescheduling/`). It is
**complete** when, after `pnpm reset` and the demo seed, each of the six roles can do its whole job in the browser with no
console script and no API tool:

| Role | Does, in the UI |
|---|---|
| Super Admin | Sets up departments, divisions, batches, subjects, rooms, faculty (and links their accounts), terms, teaching assignments; builds and publishes a division's weekly timetable; reads the audit log |
| Faculty | Reads their timetable; reschedules, cancels, substitutes, exchanges, adds extra lectures; sends a ticket for review or applies it themselves; answers review requests; reverts a change; closes issues they are mentioned on; reads notifications |
| HOD | Reads the department's timetable; raises tickets for a teacher; reviews; approves or rejects permanent changes; reverts in scope; reads the department's issues |
| CR | Reads the division's timetable; reviews theory tickets; opens, edits and closes theory issues |
| BR | The same for one batch's lab and tutorial lectures |
| Student | Reads the live timetable of their batch (changes marked) and gets notified when it changes |

Not in the definition (deferred; listed in [backend-audit.md](backend-audit.md) section 8): ticket-history timeline,
holidays/exam weeks, shared courses, room-fit as a conflict, plain-information notes, attendance events, other notification
channels.

## 2. Prerequisites (before Phase 1 starts)

Verify each; if one fails, stop and tell the person you work for.

| # | Prerequisite | How to verify |
|---|---|---|
| 1 | Windows 11, PowerShell 7, Git Bash available | |
| 2 | **Docker Desktop installed and running** | `docker version` shows a server |
| 3 | **Node 22+ and pnpm 11** | `node -v` ≥ 22; `corepack enable; pnpm -v` = 11.x |
| 4 | **A JDK.** Project targets 25; JDK 21 is what is installed | `java -version`; if 21 use `'-Dmaven.compiler.release=21'` everywhere (backend-guide.md section 5) |
| 5 | About **5 GB free memory** (MySQL, Keycloak + Postgres, Mailpit, backend, Next.js, plus Maven tests) | close heavy programs; a Maven run killed for memory is not a test failure |
| 6 | Repository on branch `om/api`, backend commit `88a8b14` or later; `frontend/docs/` present | `git log -1`, `git status` |
| 7 | `pnpm install` done; `pnpm bootstrap` done (`.env` exists with `DEMO_USER_PASSWORD`, `DEMO_ADMIN_PASSWORD`) | `Test-Path .env` |
| 8 | Stack starts: `pnpm infra:up`, wait until `docker compose ps` shows keycloak healthy and `keycloak-config` exited 0; then `pnpm dev` and the backend logs `Started BackendApplication` | open <http://localhost:3470>, sign in as `demo-admin` |
| 9 | **Baseline green**: backend `ModularityTest`, `ArchitectureTest`, `OpenApiContractIT` pass; frontend `typecheck` and `lint` pass; `test` shows exactly the 15 known failures | commands in README.md; record the numbers in your first note |
| 10 | The demo campus exists (`scripts/DEMO.md` section 4) or you will create it in Phase 1 exit check | |
| 11 | `shadcn` skill installed (`.agents/skills/shadcn`) | else `pnpm dlx skills add shadcn/ui` |

Working agreements: no commits unless asked; one phase at a time; after each task run the gate in its phase file; keep a
running list of what you ran and its result in your final report.

## 3. What each phase changes (summary)

| | Phase 1: foundations + live timetable | Phase 2: tickets | Phase 3: issues, notifications, setup, finish |
|---|---|---|---|
| **Backend, new endpoints** | `GET /directory/{departments,divisions,batches,rooms,faculties,me}`, `GET /me/scoped-roles` | `GET /tickets/{id}/reviewer-candidates`, `GET /timetable/free-rooms`, `GET /tickets?awaitingReview=true` | `PUT /faculties/{id}/account` |
| **Backend, existing responses gain** | nothing | names on tickets (author, teacher, subject, division, batch, room, reviewer, substitute, change `lecture` summary) | names on issues; `actorName`, `ticketType`, `lectureId` on notifications |
| **Backend, other** | | `access.ScopeHolders.usersHolding`, `reference.AcademicDirectory.rooms()` | `AcademicDirectory.batchCodes`, `rescheduling.TicketSummaries` port, 3 more Keycloak demo users, seed script |
| **Frontend, libs** | `dates`, `labels`, error messages, zustand, shadcn `tabs` `sheet`, `modules.ts` any-of | `lib/slot`, `lib/problems` | |
| **Frontend, features** | `campus`, `timetable` | `tickets` | `notifications`, `issues`, `setup` |
| **Frontend, routes** | `/timetable` (+ `?lecture=`), `/` redirect | `/tickets`, `/tickets/new`, `/tickets/[id]` | `/notifications`, `/issues`, `/issues/[id]`, `/admin/campus`, `/admin/timetables` |
| **Docs/tests** | unit tests for libs and timetable; API ITs | unit tests for actions, forms, screens; API ITs | 1 Playwright flow; fix 15 baseline tests; `scripts/DEMO.md` rewrite |
| **Rough effort** | 1 day | 2 days | 1.5 days |

## 4. What the product looks like at the end of each phase

**After Phase 1.** A signed-in person with any Timetia role lands on `/timetable`: a week of lectures in six day
columns, with week navigation and Division / Batch / Teacher filters. Moved, cancelled, extra, substituted and online
lectures carry badges and, for moved ones, the original time. Clicking a lecture opens a side sheet with every detail.
A person with no role sees "You have no role in Timetia yet". Nothing can be changed yet. The backend can answer "what
campus am I in" to any signed-in person.

```
 Timetable                         [< Prev]  Week of 6 Jul 2026  [Next >] [Today]
 Division [CSE-A v]  Batch [All v]  Teacher [All v]  [x] Only my lectures

 Mon 6 Jul        Tue 7 Jul         Wed 8 Jul        Thu 9 Jul   Fri 10 Jul   Sat 11 Jul
 ┌────────────┐   ┌────────────┐    ┌────────────┐
 │09:00–10:00 │   │09:00–11:00 │    │14:00–15:00 │      No         No           No
 │OOP · Theory│   │OOP · Lab A1│    │DBMS·Theory │    lectures    lectures     lectures
 │Dr Demo     │   │Dr Demo     │    │Dr Admin    │
 │R101        │   │LAB1        │    │[Online]    │
 └────────────┘   └────────────┘    └────────────┘
 ┌────────────┐   ┌────────────┐
 │10:00–11:00 │   │09:00–11:00 │
 │DBMS·Theory │   │DBMS · Lab A2│  ← [Moved] badge + "was Mon 10:00" on a moved one
 └────────────┘   └────────────┘
```

**After Phase 2.** The lecture sheet offers Reschedule, Cancel, Substitute, Exchange and Permanent change to the people
allowed. `/tickets` lists tickets in three tabs (Mine, To review, All I can see). A ticket page shows before → after,
conflicts, reviews and exactly the actions the person may take; applying updates the timetable at once. Free rooms are
suggested for a chosen slot. Review, rejection, escalation to an HOD, withdrawal and revert all work. The whole of
`scripts/DEMO.md` section 5 can be done by clicking.

```
 ← All tickets
 Reschedule · OOP theory · CSE-A                         [In review]  [conflict-free]
 Raised by Dr Demo on 4 Jul 2026 · expires when the lecture starts (Mon 6 Jul 09:00)

 ┌ Change ─────────────────────────────────────────────────────────────┐
 │ Mon 6 Jul 09:00–10:00 · R101   →   Thu 9 Jul 09:00–10:00 · R102      │
 │ Reason: Away at a conference                                         │
 └──────────────────────────────────────────────────────────────────────┘
 ┌ Reviews (revision 1) ───────────────────────────────────────────────┐
 │ Dr Admin (HOD)     Waiting                                           │
 └──────────────────────────────────────────────────────────────────────┘
 [ Apply ] (disabled: waiting for reviews)   [ Edit ]   [ Withdraw ]
```

**After Phase 3.** The header shows a bell with the unread count; `/notifications` lists what happened, each row
linking to the ticket, issue or lecture. CRs and BRs open issues; mentioned teachers see them, link their ticket and
close them. A Super Admin builds the whole campus and each division's timetable under Administration → Campus and
Timetables. Each demo role can sign in. One end-to-end test walks the main flow; the earlier baseline test failures are
fixed or explained; `scripts/DEMO.md` is a click-through.

## 5. Order and dependencies between phases

```
P1 backend directory ─► P1 frontend campus ─► P1 timetable ───────────────┐
                                                                          ├─► P2 tickets ─► P3 notifications ─┐
P2 backend names/candidates/free-rooms (can start as soon as P1 backend is merged) ┘                          ├─► finish
                                                                          P3 issues ◄─ P2 ticket links ───────┤
                                                                          P3 setup (independent of P2) ───────┘
```
Within a phase, do backend tasks first (marked **B**), regenerate the client, then frontend tasks (**F**). Tasks are
numbered so a worker can be handed "B2.1 to B2.4" or "F1.5 to F1.9".

## 6. Cut order when time runs short

1. Phase 3 `setup` screens (the seed script still builds a campus).
2. Phase 3 end-to-end test and the `DEMO.md` rewrite (keep the old file).
3. Phase 3 issues screens (the API stays; issues are optional in the demo).
4. Phase 2 `/tickets/new` extra-lecture form.
5. Phase 2 free-room suggestions UI (a plain room select from `directory/rooms` still works).

**Never cut**: the live timetable, ticket list and detail with actions, the review flow, the notification bell.

## 7. Global definition of done (per phase)

- Every task's gate passed; every checkbox in the phase file ticked.
- Backend: `ModularityTest`, `ArchitectureTest`, `OpenApiContractIT` and every new/changed IT pass; the **full** `verify`
  passes at the end of the phase (run it in the background, backend-guide.md section 5).
- Frontend: `api:generate` produces no diff after the last backend change; `typecheck`, `lint` clean; `test` shows no
  failure other than the baseline 15 (zero failures at the end of Phase 3); `build` succeeds at the end of each phase.
- The phase's **exit check** performed in a browser against the running stack, results written in the final report.
- No colour literals, palette classes or gradients in new code (`rg -n "bg-gradient|bg-linear|from-|via-|to-|#[0-9a-fA-F]{3,6}\b|(red|green|blue|yellow|orange|amber|emerald|slate|gray|zinc)-[0-9]" frontend/src/features frontend/src/components --glob '!ui/**'` returns nothing).
- No new dependency except `zustand`.
- Docs updated: this folder's tick-boxes; `timetia-docs/modules/lecture-rescheduling/changelog.md` gets one dated line per phase;
  `timetia-docs/architecture/identity/current-state.md` is updated where it lists API endpoints.
