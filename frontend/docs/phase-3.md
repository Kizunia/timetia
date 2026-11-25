# Phase 3: issues, notifications, admin setup, finish

Read first: [plan.md](plan.md), [domain.md](domain.md) sections 4, 5, 7, [api-reference.md](api-reference.md) sections 5 to 8,
[architecture.md](architecture.md), [backend-guide.md](backend-guide.md).

## 0. Purpose and result

Finish the module: notifications with a bell, issues for class and batch representatives, the Super Admin's campus and timetable
setup, demo accounts for every role, and the clean-up that makes the project deliverable.
**Result**: plan.md section 4, "After Phase 3", and the "complete" definition in plan.md section 1.

## 1. Prerequisites for this phase

- [ ] Phases 1 and 2 are finished: all checkboxes ticked, backend full `verify` green, frontend `typecheck`, `lint`, `build` green, `test` at the 15 baseline failures only.
- [ ] Tickets work end to end in the browser (Phase 2 exit check done).
- [ ] You know where Keycloak demo users are defined: `infra/keycloak/overlays/local/main.tf` (`keycloak_user.demo`, `keycloak_user.demo_admin`); the `keycloak-config` container applies it at `pnpm infra:up`.
- [ ] Order inside the phase: B3.1 to B3.3 and F3.1 to F3.4 (notifications, issues) can run in parallel with F3.5 (setup); B3.4 (demo data) before the final exit check.

## 2. What changes

| Area | Change |
|---|---|
| Changed responses | `IssueResponse` (+names), `NotificationResponse` (+`actorName`, `ticketType`, `lectureId`) |
| New endpoint | `PUT /faculties/{id}/account` |
| New ports | `reference.AcademicDirectory.batchCodes(...)`, `rescheduling.TicketSummaries` |
| Infra/demo | three more Keycloak demo users, seed script, `scripts/DEMO.md` rewrite |
| Frontend | `features/notifications`, `features/issues`, `features/setup`, header bell, two admin sections, e2e test, fix of the 15 baseline failures |

## 3. Backend tasks

### B3.1 Names on issues

1. `AcademicDirectory`: add `Map<UUID, String> batchCodes(Collection<UUID> batchIds)` (live batches only), implemented in `AcademicDirectoryService` with `campus.findBatches(ids)`.
2. `issues/application/IssueNames.java` record `(Map<UUID,String> users, Map<UUID,String> divisionCodes, Map<UUID,String> batchCodes, Map<UUID,String> facultyNames)` with `NONE`, and
   `IssueNameService.namesFor(Collection<IssueDetail>)` (`@Service`, `@NoAuthorization("adds names to issues the caller may already see")`): `UserDirectory.find` for opener and closer ids;
   `AcademicDirectory.division(id)` for division codes (one call per distinct division: acceptable, there are few; or add a bulk `divisions(ids)` if you prefer); `batchCodes`; `faculties(ids)` for mentioned ids (name).
3. `IssuesController` injects the service and `IssueMapper.toResponse(detail, names)`. `IssueResponse` gains nullable `openerName`, `divisionCode`, `batchCode` (add; api-reference.md section 5 lists `divisionCode` only: also add `batchCode`), `mentionedFaculty: List<{id, name}>`, `closedByName`.
4. Test `issues/IssueNamesApiIT extends ReschedulingApiTest` (copy setup of `IssueApiIT`): names present for opener (CR), division code `CSE-A`, mentioned faculty name "Dr Rao"; a deleted faculty member does not break the response (name null).

### B3.2 Notification enrichment

1. `rescheduling/TicketSummaries.java` (published port): `Map<UUID, TicketSummary> summariesOf(Collection<UUID> ticketIds)` with `record TicketSummary(UUID ticketId, String type, UUID lectureId)`;
   `lectureId` = the first change's `occurrenceId()` (null if none). Implement in `rescheduling/application/TicketSummaryService.java` (`@Service`,
   `@NoAuthorization("only ids and a type; used to describe the caller's own notifications")`) reading `TicketStore.find` and `changesOf(Collection)` (one bulk call).
2. `notifications/application/NotificationService.list` returns `Page<NotificationView>` where `NotificationView(Notification notification, String actorName, String ticketType, UUID lectureId)`:
   after `search`, bulk-resolve actor names (`UserDirectory.find`) and ticket summaries (`TicketSummaries`). `markRead` returns a `NotificationView` too (same helper for one).
3. `NotificationResponse` gains nullable `actorName`, `ticketType`, `lectureId`; update `NotificationsController.toResponse`.
4. `ModularityTest`: `notifications` already lists `rescheduling`; nothing else. `NotificationApiIT` (extend): after applying a ticket as Rao, Roy's/student's notification has `ticketType` `RESCHEDULE`, `lectureId` = the lecture, `actorName` non-empty; a notification for an issue has `ticketType` null.

### B3.3 Link a faculty member's account

`PUT /api/v1/faculties/{id}/account` body `{ userId: UUID|null, version: long }` → `FacultyResponse`.
1. `CatalogStore`: `Faculty updateFacultyUser(UUID id, long expectedVersion, UUID userId)`; implement in `JpaCatalogStore` (copy `renameFaculty`: optimistic version check).
2. `CatalogService.linkFacultyAccount(UUID id, long version, UUID userId)`: `@RequiresPermission(CONFIG_MANAGE)`, `@Transactional`; if `userId != null` and `UserDirectory.find(Set.of(userId))` lacks it → 400 field `userId` code `unknown_user`; not found faculty → 404 `reference.faculty_not_found`; audit `reference.faculty.account-linked` with change `user`.
3. `FacultiesController`: `@PutMapping("/{id}/account")` + request record `LinkAccountRequest(UUID userId, @NotNull Long version)`.
4. Tests in `CatalogAdminApiIT` (or new): link an existing user → the faculty's `userId` set and `/directory/me` of that user lists their assignments; unknown user → 400; stale version → 409; non-admin → 403; unlink with `null`. Add to `OpenApiContractIT`: path `/api/v1/faculties/{id}/account`.

### B3.4 Demo data

1. `infra/keycloak/overlays/local/main.tf`: add three `keycloak_user` resources copying `demo`: `demo-cr` (first name Demo, last name CR), `demo-br`, `demo-student`, emails `demo-cr@timetia.localhost` etc., password `var.demo_user_password`.
2. `scripts/demo-seed.browser.js`: after the existing steps, for each of `demo-cr`, `demo-br`, `demo-student` that exists in `GET /users?size=100` (a profile appears after the first sign-in), assign roles: CR → `scopeId: division`, BR → `scopeId: a1`, Student → `scopeId: a1`;
   for missing ones print "Sign in once as X, then run the seed again" (the seed is idempotent: it already stops when CSE exists; change it to **continue** to the role step when the campus exists, skipping creation). Add the new user ids to `window.demoCampus`.
3. `scripts/DEMO.md`: see F3.9.

### B3.5 Registration and gate

`OpenApiContractIT` list: `/api/v1/faculties/{id}/account`. **Gate B3**: new ITs, the existing `notifications.*`, `issues.*`, `reference.*` ITs, `ModularityTest`, `ArchitectureTest`, `OpenApiContractIT`; then `api:generate`.

## 4. Frontend tasks

### F3.1 Notifications (`features/notifications`)

- `api.ts`: `useNotifications({page,size,unread})`, `useUnreadCount()` (`queryKey ["unread"]`, `refetchInterval: 30_000`, `refetchIntervalInBackground: false`, `staleTime: 0`), `useMarkRead()`, `useMarkAllRead()` (invalidate `notifications`, `unread`).
- `lib/describe.ts`: `describeNotification(n): { text: string; href: string | null }` implementing the sentence table of domain.md section 5. `{kind}` = `TICKET_TYPE_LABEL[n.ticketType]?.toLowerCase() ?? "ticket"`; `{actor}` = `n.actorName ?? "Someone"`.
  `href`: for ticket types → `/tickets/<ticketId>` **if** the person can see tickets (`has("rescheduling.tickets.view") || has("rescheduling.tickets.view-in-scope")`), else `/timetable?lecture=<lectureId>` when `lectureId`, else null; for `ISSUE_MENTION` → `/issues/<issueId>`. Unit-test every type for both kinds of viewer.
- `components/NotificationBell.tsx`: client component in `SiteHeader` (only when `me` is set and `me.emailVerified`): a link `<Link href="/notifications" aria-label="Notifications, N unread">` with `lucide-react` `BellIcon` and a `Badge` showing the count (`99+` cap) when > 0. Add a `NotificationBell` import to `SiteHeader.tsx` (it is a server component; a client child is fine).
- `NotificationsScreen` (`/notifications`): tabs All / Unread; a list (`<ul>`), each row `<li>`: unread dot (`aria-label="Unread"`), the sentence as a `Link` when `href`, time (`toLocaleString`), a "Mark read" `Button variant="ghost"` for unread rows; clicking the link also marks it read (`onClick` fire-and-forget). Header button "Mark all read" (disabled when count is 0). `Pager`. Empty: "Nothing to show".
- `modules.ts`: block `notifications` with `nav.permissions: []`? The bell is the entry; do **not** add a nav link. Add only `protectedPrefixes: ["/notifications"]` (set `nav` undefined) and the proxy matcher line.
- Tests: describe table; bell shows count and hides at 0 (stub `/notifications/unread-count`); screen: tab "Unread" sends `unread=true`; mark read posts `/api/v1/notifications/{id}/read`; read-all posts and refetches.

### F3.2 Issues (`features/issues`)

- `api.ts`: `useIssues({page,size,status?,mine?})`, `useIssue(id)`, `useOpenIssue()`, `useCloseIssue(id)`, `useMentionFaculty(id)`, `useLinkTicket(id)`; invalidation per matrix.
- `lib/actions.ts`: `issueActions(issue, me, myFacultyIds)`: `close` = status OPEN and (`openerUserId === me.id` or a mentioned faculty id ∈ `myFacultyIds`); `mention` = OPEN and `openerUserId === me.id`; `link` = same as close. Test.
- `schemas.ts`: `issueSchema { sessionType, divisionId?, batchId?, title (1..200), description (≤2000), mentionedFacultyIds (≤10) }` with refines: THEORY needs `divisionId` ("Choose a division"), LAB/TUTORIAL need `batchId` ("Choose a batch"). Body builder drops the unused one.
- `IssuesScreen` (`/issues`): tabs Open / Closed / Mine; table (Title, Audience "Theory · CSE-A" / "Lab · CSE-A1" from `sessionType`, `divisionCode`, `batchCode`; Opened by `openerName`; Mentions count; Created); "Open an issue" button when `has("rescheduling.issues.open")`.
- `OpenIssueDialog`: session `Select`; division `Select` (`useDivisions`) or batch `Select` (`useBatches`) by session; the pickers show **only the places the person is CR/BR of**: filter by `useMyPlaces().divisionIds` / `batchIds` when non-empty (hint; server decides); title, description, mentions as a `Checkbox` list of `useFaculties()` filtered to the division's department when known (searchable by a text `Input`); max 10. On 201 → `/issues/<id>`.
- `IssueDetailScreen` (`/issues/[id]`): title, status badge, audience, opener, description, mentioned teachers (names), linked tickets (each a `Link` to `/tickets/<id>`; if the person cannot see tickets show the short id as text), buttons by `issueActions`: Mention (dialog with faculty `Select`), Link a ticket (dialog: a `Select` of the person's own tickets from `useTickets({mine: true, size: 100})` — a mentioned teacher links the ticket they raised), Close (confirm). Closed issues show "Closed by {closedByName} on …" and no buttons.
- `modules.ts` block `issues`: `nav.permissions: ["rescheduling.issues.view", "rescheduling.issues.view-in-scope", "rescheduling.issues.open"]`, `protectedPrefixes: ["/issues"]`; proxy matcher lines.
- Tests: schema refines; open dialog sends THEORY body `{sessionType:"THEORY", divisionId, title, mentionedFacultyIds}` with no `batchId`, LAB body with `batchId` and no `divisionId`; detail buttons per role; 409 `issues.issue_not_open` message.

### F3.3 `features/setup` (Super Admin; **first to cut**)

Routes `/admin/campus` and `/admin/timetables`. Add to `ADMIN_SECTIONS` in `components/SiteHeader.tsx`:
`{ href: "/admin/campus", label: "Campus", permission: "timetable.config.manage" }` and `{ href: "/admin/timetables", label: "Timetables", permission: "timetable.config.manage" }` (the tests in `admin-sections.test.tsx` enumerate sections: update).

`resources.ts` (config-driven; one generic table + dialogs, no per-entity screens):

```ts
export interface Field { name: string; label: string; type: "text" | "select" | "date" | "number"; required?: boolean; options?: "departments"|"divisions"|"batches"|"subjects"|"faculties"|"sessionTypes"|"roomTypes"; dependsOn?: string; maxLength?: number }
export interface Resource { key: "departments"|"divisions"|"batches"|"subjects"|"rooms"|"faculties"|"academic-terms"|"teaching-assignments";
  title: string; path: `/api/v1/${string}`; columns: { header: string; cell: (row) => ReactNode }[]; createFields: Field[]; canRename: boolean; filters?: Field[] }
```
Eight entries per api-reference.md section 7. A `ResourceTable` (`useAllPages` hook with `size: 100`, `q` text filter, filter selects), `CreateDialog` (RHF + zod built from `createFields`; select options from the admin lists), `RenameDialog` (`name` + `version`; 409 → reload),
`DeleteDialog` (`AlertDialog`: "Delete {code}? Lectures and tickets that refer to it keep working but will show a blank name."), faculty row action `LinkAccountDialog` (user `Select` from `GET /users?q=` with a debounced text input; sends `PUT /faculties/{id}/account`, "Remove link" sends `userId: null`).
Page layout: a `Tabs` strip with the eight resources, state in a zustand store (`activeResource`). Errors: `ErrorAlert`; duplicate code → field error on `code` (`reference.duplicate_code` → `{ "reference.duplicate_code": "code" }` in `applyProblem`'s map).
Teaching assignment creation: Division → Subject → Faculty → Session type → Batch (only for LAB/TUTORIAL, filtered to the division). Term: dates; `endDate` error `end_before_start` shown on the field.
Tests: one test of `ResourceTable` creating a room (POST body), one of duplicate-code error placement, one of delete confirm.

`TimetablesScreen` (`/admin/timetables`):
1. Version list: filter by division and status (`GET /timetable/versions`), table (Division, Term, Effective from, Status badge, actions Open / Delete draft), "New version" dialog (division, term from `/academic-terms`, `effectiveFrom` date; 400 `effectiveFrom` `outside_term` shown on the field).
2. Version detail (`/admin/timetables?version=<id>`, query parameter to avoid another route): entries table (Day via `DAY_LABEL`, Time, Subject/Teacher/Audience resolved from `GET /teaching-assignments?division=<id>` + `subjects` + `faculties` lists, Room code or link), "Add lecture" form (assignment `Select` of that division's assignments, day, start/end hour, mode, room/link) and per-row Delete, **only while DRAFT**; a published version is read-only with a note.
3. **Publish** button (draft only; `AlertDialog` "Publish this timetable? Lectures from {date} are created and replace the division's earlier ones."); success toast "Published: {occurrencesCreated} lectures created, {occurrencesSuperseded} replaced"; 409 `timetable.clash` shows `problem.detail` verbatim in an `ErrorAlert`; `timetable.changes_would_be_lost` and `effective_from_taken` use `MESSAGES`.
4. Add-entry errors: 409 `timetable.clash` → ErrorAlert with `detail`; 400 field errors (`roomId`, `startTime`, `dayOfWeek`, `assignmentId`).
Tests: add-entry body shape; publish success message; clash detail shown.

### F3.4 Header and home

`SiteHeader`: bell for signed-in verified users. Home (`app/page.tsx`) is unchanged from Phase 1.

### F3.5 Fix the baseline failures

The 15 failing tests (`tests/unit/avatar.test.tsx`, `import-screen.test.tsx`, `note-attachment.test.tsx`) are file-upload tests that fail before any Timetia change. Procedure:
run `pnpm --filter kibootia-frontend test avatar import-screen note-attachment` and read the first failure. Likely causes to check in this order: the `File`/`FormData`/`Request` implementation of Node 25 vs
jsdom 30 (the tests build a `Request` from a `FormData` body); `stubApi` in `tests/unit/ui.tsx` clones the request and calls `request.clone().text()` which fails for multipart bodies in this runtime. Fix in the test helper (`ui.tsx`: read multipart as `formData()` and record file names) if it is the helper,
or pin the failing test's environment. If a root cause cannot be fixed in 2 hours, mark those three files `describe.skip` with a comment naming the cause and open a note in your report; the exit rule is "no unexplained failure".

### F3.6 End-to-end test (one flow)

`tests/e2e/rescheduling.spec.ts`, pattern `tests/e2e/notes.spec.ts` (serial, two browser contexts, `signIn`, `expectNoAccessibilityViolations`, a STAMP in text). Preconditions: the stack is up and the demo seed has run; skip with `test.skip` when the demo passwords are unset or `/api/v1/departments` has no CSE.
Steps: (1) `demo` opens `/timetable`, opens a lecture, raises a reschedule to a free slot, sees `Draft`, **Sends for review** to the HOD. (2) `demo-admin` opens `/tickets` → To review → approves; the ticket is `Applied`. (3) `demo` reloads `/timetable`: the lecture shows "Moved". (4) `demo` sees a notification in the bell (count ≥ 1) and `/notifications` lists "applied".
(5) `demo` reverts it; applies the revert; the lecture is back. Run axe on `/timetable`, a ticket page, `/notifications`. The test must be re-runnable (pick a lecture date by searching for a free Thursday slot; do not assume a clean state).

### F3.7 Documentation and cleanup

- Rewrite `scripts/DEMO.md` as a click-through (same accounts and seed steps; the console snippets removed; the flows of Phase 2 exit check and Phase 3 exit check as numbered steps). Keep the "what you need / start / seed / stop" sections.
- `timetia-docs`: `modules/lecture-rescheduling/changelog.md` one dated line per phase; `research/data-model-fixes.md` F-J7: mark shared courses **deferred**; `architecture/identity/current-state.md`: list the new endpoints; `modules/lecture-rescheduling/open-questions.md`: add the deferred features of [backend-audit.md](backend-audit.md) section 8 as one row each.
- `kibootia-docs/architecture/frontend.md`: update the "Folders" line with the new feature folders.
- This folder: tick the boxes; update [backend-audit.md](backend-audit.md) so that blockers B1 to B6 say "fixed in Phase n".
- Final scan: the colour/gradient `rg` in plan.md section 7 returns nothing; no `console.log`; no `TODO` without a name; `git status` lists only intended files (no `build/` directories, no `tsconfig.tsbuildinfo`).

## 5. Exit check (the project is complete)

After `pnpm reset`, `pnpm infra:up`, `pnpm dev`, then each of the following in the browser. Record the result.

1. Sign in once as every demo account (`demo`, `demo-admin`, `demo-cr`, `demo-br`, `demo-student`), run the seed as `demo-admin` (campus + roles).
2. **Super Admin** (`demo-admin`): Administration → Campus: create a room `R103` (appears), rename it, delete it, try a duplicate code (field error); link a faculty account; Administration → Timetables: create a draft for CSE-A, add two lectures, provoke a clash (same teacher, same hour) and read the message, fix, **Publish**; the `/timetable` shows the new lectures; Audit log shows the actions.
3. **Faculty** (`demo`): all of Phase 2's exit check still works; bell shows notifications after `demo-admin` approves.
4. **HOD** (`demo-admin`): reviews and approves a permanent change; reverts a teacher's change; sees the department's issues.
5. **CR** (`demo-cr`): `/timetable` shows the division; opens a THEORY issue mentioning Dr Demo; cannot open a LAB issue for A1 (403 message shown); reviews a theory ticket asked of them; does not see lab tickets.
6. **BR** (`demo-br`): opens a LAB issue for A1; sees A1's lab tickets only.
7. **Student** (`demo-student`): `/timetable` shows theory + A1 lab; no "Change this lecture"; no Tickets or Issues links; gets a bell notification when a lecture of theirs is rescheduled; the notification link opens the **lecture** (not a 404 ticket).
8. **Mentioned teacher** (`demo`): sees the CR's issue in the bell and `/issues`, raises a ticket, links it, closes the issue.
9. Run everything: backend full `verify` green; frontend `lint`, `typecheck`, `test` (zero failures), `build` green; `test:e2e` (the new flow passes; the older specs still pass or are skipped for stated reasons).
10. 400 px width spot check on `/timetable`, `/tickets/<id>`, `/notifications`, `/issues`.

## 6. Checklist

- [x] B3.1 issue names  - [x] B3.2 notification enrichment (plus `ticketVisible`)  - [x] B3.3 faculty account link  - [x] B3.4 demo data (through `pnpm seed` accounts, no new Keycloak users)  - [x] B3.5 OpenApi list, Gate B3, `api:generate`
- [x] F3.1 notifications  - [x] F3.2 issues  - [x] F3.3 setup  - [x] F3.4 header  - [x] F3.5 baseline failures (none left)  - [x] F3.6 e2e  - [x] F3.7 docs and cleanup
- [x] Backend full `verify` green (before the `ticketVisible` change; see `HANDOVER-PHASE-3.md`)  - [x] Frontend all green  - [ ] Exit check 1 to 10 done (partly; see the handover)  - [x] changelog line added
