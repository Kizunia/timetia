# Handover: Phase 1 (foundations + live timetable, read path)

Branch `dog`. Written 2026-11-16 after implementing `frontend/docs/phase-1.md`.
The plan documents are in `frontend/docs/` (read `README.md` first, then `phase-2.md` for what comes next).

## 1. Status at a glance

| Part | State |
|---|---|
| Backend: 7 new read endpoints, tests, OpenAPI list | Done and green (Gate B1) |
| Generated client (`openapi.json`, `schema.gen.ts`) | Regenerated, in sync |
| Frontend: libs, `campus`, `timetable`, routes, tests | Done (Gate F1: typecheck, lint, test, build) |
| Full backend `verify` | Done and green: `BUILD SUCCESS`, 250 unit + 563 integration = 813 tests, 0 failures, 37 min (JDK 27) |
| Browser exit check (phase-1.md section 5) | **Not done.** See section 4 |
| E2E suite | Not run (the plan says Phase 3) |

## 2. What is implemented

### Backend (modules `reference` and `access`; no schema change, no migration, no new module edge)

New endpoints. All are read-only and open to any signed-in person with a **verified email** (unverified: 403, no token: 401).

| Endpoint | Returns |
|---|---|
| `GET /api/v1/directory/departments` | `{id, code, name}[]` |
| `GET /api/v1/directory/divisions` | `{id, code, name, departmentId}[]` |
| `GET /api/v1/directory/batches?division=` | `{id, code, name, divisionId}[]` (filter optional) |
| `GET /api/v1/directory/rooms` | `{id, code, name, roomType, capacity?}[]` |
| `GET /api/v1/directory/faculties?department=` | `{id, code, name, departmentId, hasAccount}[]` (filter optional) |
| `GET /api/v1/directory/me` | `{faculty[], assignments[]}`: the caller's faculty record(s) and what they teach; both empty for a non-teacher |
| `GET /api/v1/me/scoped-roles` | `{roleId, roleName, scopeType, scopeId?, scopeCode?}[]`: the caller's own roles in places |

Lists are unpaged, at most 500 rows, sorted by code (faculty by name). Deleted rows drop out.

Files:
- New: `reference/application/DirectoryService.java`, `reference/web/DirectoryController.java`, `reference/web/DirectoryResponses.java` (response records, nested in one holder), `access/web/MyScopedRolesController.java`.
- Changed: `access/application/ScopedAssignmentService.java` (adds `myScopedRoles()`), `access/web/ScopedUserRolesController.java` (`toResponses` is now package-private so both controllers share it).
- Tests: `reference/DirectoryApiIT` (9), `access/MyScopedRolesApiIT` (6), `OpenApiContractIT` pinned list extended by the 7 paths. `ArchitectureTest` (10) and `ModularityTest` (4) pass unchanged.

Note for anyone reading the plan: the persistence layer is plain JDBC now (commit `jdbc to sql`), not JPA as the plan text assumes. The new code uses the existing `CampusStore`, `CatalogStore`, `TeachingAssignmentStore` ports, so this made no difference to the design.

### Frontend

- **Dependencies:** `zustand` (exact version), shadcn `tabs` and `sheet` (added with the CLI; `button.tsx` was deliberately not overwritten). `tabs` is installed but not used yet.
- **`src/lib/dates.ts`:** pure ISO-date helpers via `Date.UTC`. Tests pass under both `TZ=America/Los_Angeles` and `TZ=Asia/Kolkata`.
- **`src/lib/labels.ts`:** the label maps of `domain.md` section 6, each guarded with `satisfies` against the generated types, so a new backend enum value breaks the build.
- **`src/lib/api/problem.ts`:** the Timetia error messages of `domain.md` section 7.
- **Registry:** `modules.ts` nav now takes `permissions: readonly string[]` (any-of); notes block updated; `timetable` block added. `proxy.ts` matcher has `/timetable` and `/timetable/:path*`.
- **`features/campus`:** `api.ts` (`useDepartments`, `useDivisions`, `useBatches`, `useRooms`, `useFaculties`, `useMyCampus`, `useMyScopedRoles`, 5-minute stale time), `lib/names.ts` (`nameIndex`, dash for unknown ids), `lib/places.ts`, `useMyPlaces.ts` (facultyIds, isFaculty, divisionIds, batchIds, departmentIds, roles).
- **`features/timetable`:** `api.ts` (`useLiveRange` reads all pages at size 100, `useLiveWeek`, `useLecture`), `store.ts` (zustand; changing the division clears the batch), `lib/grid.ts`, `lib/describe.ts`, components `LectureCard`, `WeekGrid`, `Filters`, `LectureSheet` (has an `actions` slot, empty for now), and `TimetableScreen` (week navigation, filters, loading/error/empty states, `?lecture=<id>` deep link).
- **Routes:** new `/timetable` page (inside `<Suspense>`, `force-dynamic`). `/` redirects to `/timetable` for anyone holding a `timetable.live.*` permission; otherwise it shows the "You have no role in Timetia yet" sentence.
- **E2E helper:** `tests/e2e/support.ts` `signIn` now waits for either the `signed-in` marker or the `/timetable` URL.
- **Generated:** `backend/openapi.json` and `frontend/src/lib/api/schema.gen.ts`.
- **Docs:** phase-1 checklist ticked (except the two open items), one dated line in `timetia-docs/modules/lecture-rescheduling/changelog.md`, endpoint rows in `timetia-docs/architecture/identity/current-state.md`.

### Test results (as of writing)

| Check | Result |
|---|---|
| `DirectoryApiIT` / `MyScopedRolesApiIT` / `OpenApiContractIT` | 9 / 6 / 4 passed |
| `ArchitectureTest` / `ModularityTest` | 10 / 4 passed |
| Frontend `typecheck`, `lint`, `build` | clean |
| Frontend `test` | 255 run: 240 pass, **15 fail, exactly the known baseline** (`avatar`, `import-screen`, `note-attachment`) |
| New frontend tests | `dates` (9), `labels` (3), `timetable-lib` (names, places, grid, describe, store), `timetable-screen` (12), plus additions to `modules` and `api-problem` |
| Colour-literal scan (plan.md section 7) | clean |

## 3. Decisions and deviations worth knowing

- The directory response records are nested in `DirectoryResponses` instead of seven files; the OpenAPI schema names (`DirectoryRoomResponse`, `MyCampusResponse`, ...) are distinct from the admin ones.
- `useMyPlaces` is in its own file (`campus/useMyPlaces.ts`); `useMyCampus` stays in `campus/api.ts`.
- The unit tests for names, grid and describe are combined in `timetable-lib.test.ts` rather than the separate files the plan names.
- "Only my lectures" is stored as `onlyMine` in the zustand store as well as `facultyId`; choosing a teacher by hand unticks it.
- Build setup: the machine has JDK 27 (`JAVA_HOME` still points at 21). Backend commands were run with `JAVA_HOME='C:\Program Files\Java\jdk-27.0.1.0.3'` and **without** the `-Dmaven.compiler.release=21` override. `CLAUDE.md` still says JDK 21 only.

## 4. What remains from Phase 1

1. **Browser exit check** (phase-1.md section 5, eight steps). Needs the real stack: `pnpm infra:up`, `pnpm dev`, the demo campus seeded per `scripts/DEMO.md` section 4, accounts `demo`, `demo-admin`, a Student of batch A1 and a user with no role, in separate browser profiles. Nothing has been checked in a browser yet: layout at 400 px, the redirect from `/`, the lecture sheet, and the Cancelled/Moved badges are covered only by unit tests. Write the results (with test counts) into the report and tick the box.
(`build-full.txt` in the repo root is the log of the full verify. It is not committed and not git-ignored; delete it.)

## 5. Things to watch

- E2E specs that sign in and then expect `getByTestId("signed-in")` will not find it for users who are redirected to `/timetable`. Only the `signIn` helper was adjusted, as instructed; specs that assert the marker directly after their own sign-in steps may still need attention in Phase 3.
- The `build/` folder Spring Modulith writes at the repo root appears if Maven is run from outside `backend/`. It was removed; delete it if it comes back.
- Do not run `OpenApiContractIT` and `api:generate` at the same time.

## 6. Next phases

### Phase 2: tickets (about 2 days; spec in `frontend/docs/phase-2.md`)

Prerequisite: Phase 1 finished, including the open items above.

Backend:
- B2.1 names on ticket responses (`TicketNames`, `TicketNameService`; new nullable fields on `TicketResponse`, `ChangeResponse`, `TargetResponse`, `ReviewResponse`).
- B2.2 `GET /tickets/{id}/reviewer-candidates` (adds `access.ScopeHolders.usersHolding` and the `rescheduling → access` module edge, which needs the pin in `ModularityTest`).
- B2.3 `awaitingReview` filter on `GET /tickets`.
- B2.4 `GET /timetable/free-rooms` (adds `AcademicDirectory.roomOptions()`).
- B2.5 register the new paths in `OpenApiContractIT`, regenerate, run the gate.

Frontend (`features/tickets`):
- F2.1 registry block and proxy matcher for `/tickets`.
- F2.2 `api.ts` (list, detail, create, edit, apply, submit, review, escalate, withdraw, revert, reviewer candidates, free rooms).
- F2.3 pure logic with table-driven tests: `availableActions`, slot conversion (`lib/slot`), problem remapping (`lib/problems`).
- F2.4/F2.5 components and the raise dialog and forms for all ticket types.
- F2.6/F2.7 screens and routes (`/tickets`, `/tickets/new`, `/tickets/[id]`); wire `LectureSheet`'s `actions` slot (the slot exists now).
- F2.8 unit tests; exit check with two profiles (`demo`, `demo-admin`), i.e. `scripts/DEMO.md` section 5 done by clicking.

### Phase 3: issues, notifications, setup, finish (about 1.5 days; spec in `frontend/docs/phase-3.md`)

- Backend: names on issues, notification enrichment (`actorName`, `ticketType`, `lectureId`), `PUT /faculties/{id}/account`, demo seed data and extra Keycloak demo users.
- Frontend: `features/notifications` (header bell, `/notifications`), `features/issues`, `features/setup` (admin campus and timetable screens; first thing to cut if time is short), header and home changes.
- Fix the 15 baseline unit-test failures, add one Playwright end-to-end flow, rewrite `scripts/DEMO.md`, final docs and cleanup.

Cut order if time runs short is in `frontend/docs/plan.md` section 6. Never cut: the live timetable, ticket list and detail with actions, the review flow, the notification bell.
