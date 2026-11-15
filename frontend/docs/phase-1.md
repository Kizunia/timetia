# Phase 1: foundations + live timetable (read path)

Read first: [plan.md](plan.md) sections 2 and 7, [domain.md](domain.md) sections 1, 2, 6, 7, [api-reference.md](api-reference.md)
sections 1 to 3, [architecture.md](architecture.md), [backend-guide.md](backend-guide.md).

## 0. Purpose and result

Give every signed-in person with a Timetia role a working **timetable page**, and give the API the "who am I on campus"
and campus-lookup endpoints every later screen needs. Today only a Super Admin can read campus data (blocker B1, B2 in
[backend-audit.md](backend-audit.md)); after this phase any verified person can read names and their own assignments.

**Result**: see plan.md section 4, "After Phase 1". Nothing in the timetable can be changed yet.

## 1. Prerequisites for this phase

Everything in plan.md section 2 holds, plus:
- [ ] The demo campus is seeded (`scripts/DEMO.md` section 4): `demo` (Faculty), `demo-admin` (Faculty + HOD of CSE + Super Admin), department CSE, division CSE-A, batches A1/A2, a four-week term starting next Monday, the weekly timetable published.
- [ ] You can sign in as `demo` and `demo-admin` in two different browser profiles.
- [ ] For the Student check later, a third account exists: ask the person you work for to sign in once as any new user, or use `demo` and give it the Student role of batch A1 temporarily on `/admin/users/<id>` (Roles in a place).
- [ ] Baseline numbers recorded (plan.md prerequisite 9).

## 2. What changes

**Backend (module `reference` and `access`)**: 6 new read endpoints under `/api/v1/directory`, 1 under `/api/v1/me`. No schema
change, no migration, no new module dependency edge.

**Frontend**: 1 dependency (`zustand`), 2 shadcn components (`tabs`, `sheet`), 3 shared lib files, 1 edit to the module registry
and proxy matcher, `campus` and `timetable` features, 1 new route, 1 changed route (`/`).

## 3. Backend tasks

### B1.1 `DirectoryService` (reference/application)

Create `reference/application/DirectoryService.java`:

```java
@Service
public class DirectoryService {
	public record DepartmentRef(UUID id, String code, String name) {}
	public record DivisionRef(UUID id, String code, String name, UUID departmentId) {}
	public record BatchRef(UUID id, String code, String name, UUID divisionId) {}
	public record RoomRef(UUID id, String code, String name, RoomType roomType, Integer capacity) {}
	public record FacultyRef(UUID id, String code, String name, UUID departmentId, boolean hasAccount) {}
	public record MyAssignment(UUID id, UUID divisionId, String divisionCode, UUID batchId, String batchCode,
			UUID subjectId, String subjectCode, String subjectName, UUID facultyId, SessionType sessionType) {}
	public record MyCampus(List<FacultyRef> faculty, List<MyAssignment> assignments) {}

	// each: @Transactional(readOnly = true) @NoAuthorization("campus names are not confidential; any verified person reads them")
	public List<DepartmentRef> departments()        // authorization.requireVerified() first
	public List<DivisionRef> divisions()
	public List<BatchRef> batches(UUID divisionId)  // divisionId nullable = all
	public List<RoomRef> rooms()
	public List<FacultyRef> faculties(UUID departmentId)
	public MyCampus myCampus()
}
```
Implementation notes:
- Inject `CampusStore`, `CatalogStore`, `TeachingAssignmentStore`, `AcademicDirectory` (the published interface; same module, fine), `Authorization`.
- Lists: `store.searchX(..., null /*q*/, PageRequest.of(0, 500, Sort.by("code")))` and map `getContent()`. `searchDepartments(String, Pageable)`, `searchDivisions(UUID dept, String, Pageable)`, `searchBatches(UUID division, String, Pageable)`, `searchRooms(String, Pageable)`, `searchFaculty(UUID dept, String, Pageable)` (sort faculty by `name`). Check the exact sort property names on the entities (`code`, `name`) in `reference/persistence/*Entity.java`; the controllers' `SORTABLE` maps show them.
- `myCampus()`: `UUID me = authorization.currentUserId()`; `facultyIds = catalog.findFacultyIdsOfUser(me)`; `faculty = catalog.findFacultyByIds(facultyIds)` (has `code`); `assignmentRows = assignmentsStore.findByFaculty(facultyIds)`; `details = directory.assignments(ids)` gives codes/names; map in a stable order (division code, subject code, session type). Empty lists for a non-faculty.
- `hasAccount = faculty.userId() != null`.
- `RoomType` is `reference.application.RoomType` (enum CLASSROOM, TUTORIAL_ROOM, LAB); the record is in the same package so it is fine, and the web layer maps to the response record's enum/string.
- `SessionType` is `academic.SessionType` (already imported by `TeachingAssignment`).

### B1.2 `DirectoryController` (reference/web)

`@RestController @RequestMapping("/api/v1/directory")`, package-private, endpoints per api-reference.md section 2:
`GET /departments`, `/divisions`, `/batches?division=`, `/rooms`, `/faculties?department=`, `/me`. Responses are new records in
`reference/web/` (`DirectoryItemResponse` types: copy the field list from the service records, with `@Schema(requiredMode = REQUIRED)` on non-null
fields and `@Schema(nullable = true)` on `capacity`, `batchId`, `batchCode`). Each returns a plain `List<...>` (unpaged). Add
Javadoc saying "at most 500 rows".

### B1.3 `MyScopedRolesController` (access)

- `ScopedAssignmentService`: add
  ```java
  @Transactional(readOnly = true)
  @NoAuthorization("a person reads the roles they hold in places; it is their own data")
  public List<ScopedRole> myScopedRoles() { return view(this.authorization.currentUserId()); }
  ```
  (`view(UUID)` is the private helper already used by `scopedRolesOf`; the class already has `authorization`.)
- `access/web/MyScopedRolesController.java`: `@RequestMapping("/api/v1/me/scoped-roles")`, `GET` returns `List<ScopedRoleResponse>`
  (reuse the mapping in `ScopedUserRolesController.toResponses`: copy it, or make it a package-private static used by both).
- An unverified caller: call `this.authorization.requireVerified()` first (roles of an unverified person grant nothing anyway, but do not list them).

### B1.4 Tests

New `backend/src/test/java/com/kibootia/backend/reference/DirectoryApiIT.java extends TimetableApiTest`:
1. a Student (`personWith("Student", batchA1)`) reads `/directory/rooms` → 200 with 4 rooms sorted by code (`LAB1`, `R101`, `R102`, `R201`), roomType and capacity present.
2. an **unverified** person → 403 on every directory endpoint (use `unverified(uuid)` from `AdminApiTest`).
3. no token → 401 (MockMvc without `.with(...)`).
4. `/directory/batches?division=<CSE-A>` → exactly `CSE-A1`, `CSE-A2`; without the filter → three batches.
5. `/directory/faculties?department=<CSE>` → Rao, Roy (`hasAccount: true`); a faculty created without `userId` → `hasAccount: false`.
6. `/directory/me` as `RAO_USER` after `publishStandardTimetable()`: `faculty.length() == 1`, assignments are Rao's `theoryOop` (THEORY, no batch) and `labA1` (LAB, `batchCode` `CSE-A1`), each with `subjectCode` `OOP`; as a Student → `{ "faculty": [], "assignments": [] }`.

New `access/MyScopedRolesApiIT extends TimetableApiTest`: a person given `CR` of the division sees one item
(`roleName` CR, `scopeType` DIVISION, `scopeId` = division, `scopeCode` = `CSE-A`); Rao (Faculty) sees `roleName` Faculty with
`scopeType` GLOBAL and no `scopeId` (**verify this actual value in the test and fix the doc if the platform reports it
differently**); a person with no role sees `[]`; unverified → 403.

Update `OpenApiContractIT.describesTheApi` with the 7 new paths: `/api/v1/directory/departments`, `/divisions`, `/batches`,
`/rooms`, `/faculties`, `/me` (all prefixed `/api/v1/directory`) and `/api/v1/me/scoped-roles`.

**Gate B1**: run `DirectoryApiIT`, `MyScopedRolesApiIT`, `OpenApiContractIT`, then `ModularityTest,ArchitectureTest`
(backend-guide.md section 5); all green. Then `pnpm --filter kibootia-frontend api:generate` and check
`schema.gen.ts` now has `"/api/v1/directory/rooms"` etc.

## 4. Frontend tasks

### F1.1 Dependencies

```powershell
pnpm --filter kibootia-frontend add --save-exact zustand
Set-Location frontend
pnpm dlx shadcn@latest add tabs sheet        # after loading the shadcn skill; style radix-nova is in components.json
```
Check the generated `src/components/ui/tabs.tsx` and `sheet.tsx` for gradient or colour literals; there must be none
(delete any). Commit nothing.

### F1.2 `src/lib/dates.ts` (+ `tests/unit/dates.test.ts`)

All functions pure; ISO date strings `YYYY-MM-DD`; arithmetic through `Date.UTC` so the machine's zone never shifts a date.

```ts
export type IsoDate = string;
export const parseIso = (iso: IsoDate): { y: number; m: number; d: number };
export const toIso = (y: number, m: number, d: number): IsoDate;               // pads
export const addDays = (iso: IsoDate, n: number): IsoDate;
export const weekday = (iso: IsoDate): 1|2|3|4|5|6|7;                          // Monday = 1, Sunday = 7
export const startOfWeek = (iso: IsoDate): IsoDate;                           // the Monday on or before
export const weekDays = (weekStart: IsoDate): IsoDate[];                      // Monday..Saturday (6 items)
export const todayIso = (now: Date = new Date()): IsoDate;                    // the browser's LOCAL date
export const formatDay = (iso: IsoDate): string;                              // "Mon 6 Jul"
export const formatDayLong = (iso: IsoDate): string;                          // "Monday 6 July 2026"
export const formatWeek = (weekStart: IsoDate): string;                       // "6 Jul – 11 Jul 2026" (year of the end date)
export const hhmm = (time: string): string;                                   // "09:00:00" -> "09:00"
export const hourOf = (time: string): number;                                 // "09:00:00" -> 9
export const toApiTime = (hour: number): string;                              // 9 -> "09:00"
export const compareIso = (a: IsoDate, b: IsoDate): number;
```
Tests: `startOfWeek("2026-07-06") === "2026-07-06"`, `startOfWeek("2026-07-12") === "2026-07-06"` (Sunday), month/year
rollover (`addDays("2026-12-31", 1) === "2027-01-01"`), `weekday` of a Sunday is 7, `weekDays` returns 6 entries ending Saturday,
`hhmm("09:00:00") === "09:00"`, `formatDay("2026-07-06") === "Mon 6 Jul"`; run the file once with `TZ=America/Los_Angeles`
and once with `TZ=Asia/Kolkata` (`$env:TZ='...'; pnpm --filter kibootia-frontend test dates`) to prove zone independence.
Use `Intl.DateTimeFormat("en-GB", { weekday: "short", day: "numeric", month: "short", timeZone: "UTC" })` on a UTC date for
formatting.

### F1.3 `src/lib/labels.ts`

Paste the constants of domain.md section 6 verbatim (typed with `as const`). Add `export type TicketStatus = keyof typeof TICKET_STATUS_LABEL` etc. only if used. Unit test: every enum value of the generated schema has a label, e.g.
`for (const s of ["DRAFT",...]) expect(TICKET_STATUS_LABEL[s]).toBeTruthy()`; and a compile-time guard
`satisfies Record<components["schemas"]["TicketResponse"]["status"], string>` so a new backend status breaks the build.

### F1.4 Error messages

Add the block of domain.md section 7 to `MESSAGES` in `src/lib/api/problem.ts`. Extend `tests/unit/api-problem.test.ts` with
one assertion: `messageFor(new ApiError(409, {code:"rescheduling.lecture_started", status:409, title:"x"}))` equals the new text.

### F1.5 Module registry, proxy, header

1. `src/modules.ts`: change `FeatureModule.nav` to `{ href; label; permissions: readonly string[] }`; `moduleLinks(permissions)` shows a
   link when `module.nav.permissions.some((p) => permissions.includes(p))`; change the `notes` block to
   `permissions: ["notes.notes.view"]`; add the timetable block (architecture.md section 4). Do **not** add tickets/issues/
   notifications blocks yet; each later phase adds its own.
2. `src/proxy.ts`: add `"/timetable", "/timetable/:path*", // module:timetable` to `matcher`.
3. Update `tests/unit/modules.test.ts`: the first test iterates `entry.nav.permissions`: for each permission alone the link
   shows; with `[]` it does not. Keep the other two tests; check `/timetable-elsewhere` is not a module path.
4. `scripts/remove-module.mjs` looks for the tags; adding the tagged block is enough.

### F1.6 `features/campus`

- `api.ts`: `useDirectory<K extends "departments"|"divisions"|"batches"|"rooms"|"faculties">(kind, params?)` → `GET /api/v1/directory/{kind}` with
  `staleTime: 5 * 60_000`; typed by overloads (one hook per kind is fine: `useDepartments()`, `useDivisions()`, `useBatches(divisionId?)`,
  `useRooms()`, `useFaculties(departmentId?)`). `useMyCampus()` (`GET /directory/me`) and `useMyScopedRoles()` (`GET /me/scoped-roles`).
  Query keys: `["directory", kind, params]`, `["me-campus"]`, `["my-scoped-roles"]`.
- `lib/names.ts`: `nameIndex<T extends {id: string}>(items: T[] | undefined, label: (t: T) => string): (id: string | null | undefined) => string`
  returning `"—"` for an unknown or null id. Test it.
- `useMyPlaces()` in `api.ts` or `useMyCampus.ts`: combines `me-campus` and `my-scoped-roles` into
  `{ facultyIds: string[], isFaculty: boolean, divisionIds: string[], batchIds: string[], departmentIds: string[], roles: string[], isLoading }`.
  Used to preselect filters and decide hints. Division/batch/department ids come from `scopedRoles` by `scopeType`.

### F1.7 `features/timetable`

`api.ts`
- `useLiveRange(from, to, filters)`: fetch all pages of `GET /api/v1/timetable/live` with `size: 100`, loop until
  `page.number + 1 >= page.totalPages`, concatenating `items`; `queryKey: ["live", {from, to, division, batch, faculty}]`;
  `placeholderData: keepPreviousData`. Filters with value `null` are omitted from the query. The range may not exceed
  62 days (the server answers 400 otherwise).
- `useLiveWeek(weekStart, filters)` = `useLiveRange(weekStart, addDays(weekStart, 6), filters)`. Phase 2 reuses
  `useLiveRange` for the exchange picker.
- `useLecture(id)`: `GET /api/v1/timetable/live/{id}`, `queryKey: ["lecture", id]`, `enabled: Boolean(id)`.
- `export type Lecture = components["schemas"]["LiveLectureResponse"]`.

`store.ts`: as architecture.md section 3.2 (`weekStart`, `divisionId`, `batchId`, `facultyId`, `onlyMine`, `selectedLectureId`).
`setFilters` clears `batchId` when `divisionId` changes.

`lib/grid.ts` (+ `tests/unit/timetable-grid.test.ts`)
```ts
export interface DayBucket { date: IsoDate; lectures: Lecture[] }
export function groupByDay(lectures: Lecture[], weekStart: IsoDate): DayBucket[]
```
Six buckets Monday to Saturday, in order, each with that date's lectures sorted by `startTime`, then `divisionCode`, then `batchCode`,
then `id`. A lecture whose `date` is outside the six days (a Sunday, which cannot exist) is dropped. Tests: empty week → six empty buckets;
sorting; two parallel labs on the same day are both kept and ordered by batch code; a cancelled lecture stays in its bucket.

`lib/describe.ts`: `lectureTitle(l)` → `"OOP"` (subjectCode) or `"Deleted subject"`; `lectureWhere(l)` → `roomCode` or `"Online"` for ONLINE or `"—"`; `lectureAudience(l)` →
`"Theory · CSE-A"` / `"Lab · CSE-A1"`; `lectureFlags(l)` → ordered list of `{label, variant}`:
cancelled → `{ "Cancelled", "destructive" }`; extra → `{ "Extra", "outline" }`; moved (originalDate present) → `{ "Moved", "secondary" }`;
changed but not moved and not cancelled and not extra and not substituted → `{ "Changed", "secondary" }`; substituted (originalFacultyId) → `{ "Substitute", "outline" }`;
ONLINE → `{ "Online", "outline" }`. Test each.

`components/LectureCard.tsx`: a `<button type="button">` (so keyboard works) styled as a `Card`-like block
(`rounded-lg border bg-card p-3 text-left hover:bg-muted focus-visible:outline-2 focus-visible:outline-ring`), showing
`hhmm(start)–hhmm(end)`, bold `lectureTitle`, `lectureAudience`, teacher (`facultyName ?? "—"`), `lectureWhere`, then the flag badges (`Badge`).
Cancelled → `line-through` on the title and `text-muted-foreground` on the card. Moved → a small muted line `was {formatDay(originalDate)} {hhmm(originalStartTime)}`.
`aria-label` summarising all of the above in one sentence for screen readers. Props: `lecture`, `onSelect`.

`components/WeekGrid.tsx`: props `weekStart`, `lectures`, `onSelect`. Renders `groupByDay`; container
`grid gap-3 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-6`; each day is `<section aria-labelledby>` with heading `formatDay(date)`
(today's heading gets `aria-current="date"` and a `Badge` "Today"), then `<ul>` of `<li><LectureCard/></li>` or a muted "No lectures".
(Same component serves phones: the grid collapses to one column. There is no separate `DayList`.)

`components/Filters.tsx`: props from the store and directory hooks. Controls: Division `Select` ("All divisions" + `useDivisions()`), Batch `Select`
(enabled when a division is chosen; `useBatches(divisionId)`), Teacher `Select` (`useFaculties()`), and, only when `myCampus.isFaculty`,
a `Checkbox` "Only my lectures" (sets `facultyId` to the person's first faculty id; unchecking clears it). shadcn `Select` cannot hold an empty value:
use the sentinel `"all"`. Do not hide filters by role; the server limits results.

`components/LectureSheet.tsx`: shadcn `Sheet` (side right). Props `lectureId`, `onClose`, `actions?: ReactNode`. Uses `useLecture`. Content:
title (subject name and code), `Badge` flags, a definition list: When (`formatDayLong(date)`, `hhmm–hhmm`), Where (room code or "Online" + link as
`<a href target="_blank" rel="noreferrer">` when `meetingLink`), Who (`facultyName`; for a substitute "Substitute for {name of originalFacultyId}" using `useFaculties()` index),
For (`lectureAudience`), and, when moved, "Moved from {formatDay(originalDate)} {hhmm}–{hhmm}". Loading → Skeleton; error → `ErrorAlert`
(404 text from `timetable.lecture_not_found`). `{actions}` renders in a footer region (empty in Phase 1).

`TimetableScreen.tsx`
1. Reads the store; on mount, if `useSearchParams().get("lecture")` is set: fetch that lecture, `setWeekStart(startOfWeek(lecture.date))`, `select(id)`; closing the sheet removes the query parameter (`router.replace("/timetable")`).
2. Header row: title "Timetable", `Prev` / `Next` / `Today` buttons (`weekStart ∓ 7`), and `formatWeek(weekStart)` in a live region (`aria-live="polite"`).
3. `Filters`, then states: loading → six Skeleton columns; error → `ErrorAlert title="The timetable could not be loaded"`; success with zero lectures in the whole week and no filters → muted card "There are no lectures this week" and, if the person has no roles at all (`me.permissions` lacks every `timetable.live.*`), the text "You have no role in Timetia yet. Ask an administrator."; else `WeekGrid`.
4. `LectureSheet` with `lectureId = selectedLectureId`.

### F1.8 Routes

- `src/app/timetable/page.tsx`: `<PageShell width="wide"><TimetableScreen /></PageShell>` with `dynamic = "force-dynamic"`. Because `TimetableScreen` uses `useSearchParams`, wrap it in `<Suspense>` in the page.
- `src/app/page.tsx` (home): for a signed-in user whose `me.permissions` includes any of `timetable.live.view`, `timetable.live.view-own`,
  `timetable.live.view-all`, `redirect("/timetable")` (server `redirect` from `next/navigation`); otherwise keep the existing signed-in
  content (it carries `data-testid="signed-in"`, which e2e tests use) and add the sentence "You have no role in Timetia yet.
  Ask an administrator." A Super Admin holds `view-all`, so they are redirected too; their admin links stay in the header.
  Existing e2e tests (`tests/e2e/*.spec.ts`) sign in and then expect `getByTestId("signed-in")` on `/`, which the redirect
  would break. Fix `signIn` in `tests/e2e/support.ts` so that after sign-in it waits for either that test id or the URL
  `/timetable` (give the timetable screen's `<h1>` `data-testid="timetable-page"`). Keep this edit minimal and run
  `typecheck`; the e2e suite itself is not run until Phase 3.

### F1.9 Unit tests (all with `stubApi`, `renderScreen`, `meWith`)

`tests/unit/timetable-screen.test.tsx`:
1. calls `GET /api/v1/timetable/live` with `from` = the Monday of the current week, `to` = Sunday, `size=100`; shows each returned lecture's subject; a cancelled lecture has the "Cancelled" badge; a moved lecture shows "Moved" and `was Mon 6 Jul 09:00`.
2. paging: first page `totalPages: 2` → the screen requests `page=1` and shows lectures from both pages.
3. Next button → a new request with `from` + 7 days.
4. choosing a division in Filters → request carries `division=<id>`; choosing a batch → `batch=<id>`; "Only my lectures" present only when `/directory/me` returned a faculty record.
5. clicking a lecture opens the sheet showing `GET /timetable/live/{id}` details; a 404 shows the `timetable.lecture_not_found` message.
6. empty week and `me.permissions` without live permission → "You have no role in Timetia yet".
7. a 500 → ErrorAlert "The timetable could not be loaded".
`tests/unit/campus-names.test.ts`, `tests/unit/timetable-describe.test.ts`, `dates.test.ts`, `timetable-grid.test.ts`, `labels.test.ts`.

**Gate F1**: `typecheck`, `lint`, `test` (only the 15 baseline failures), `build`.

## 5. Exit check (browser, real stack)

1. `pnpm infra:up`, `pnpm dev`, backend started; migrate: no schema change in this phase.
2. As **demo-admin**: open `/` → lands on `/timetable`. The week shows Mon 9–10 OOP, 10–11 DBMS, Tue the two labs, Wed 14–15 DBMS **Online** (term starts next Monday: click Next until the term). Choose Division CSE-A → same lectures; choose Batch A1 → A1's lab and both theory lectures; the Teacher filter narrows to one person.
3. As **demo** (second browser profile, Faculty): a faculty member sees every lecture of a division they teach in, so all CSE-A
   lectures are listed, including the A2 lab taught by Dr Admin. Tick "Only my lectures": only OOP theory and the A1 lab remain.
4. As a **Student of batch A1** (give a third account that role on `/admin/users/<id>`, "Roles in a place"): theory lectures and the A1 lab are listed, the A2 lab is not.
5. Use DEMO.md section 5 console snippet to cancel the Monday OOP lecture and move the DBMS lecture; reload `/timetable`: "Cancelled" and "Moved / was …" appear; click the moved one: the sheet shows the original slot.
6. Open `/timetable?lecture=<id>`: the right week opens with the sheet.
7. Narrow the window to about 400 px: one column, no horizontal page scroll.
8. As a user with no roles: "You have no role in Timetia yet."

Write the results (including the numbers of tests run) in your report. Tick the boxes below.

## 6. Checklist

- [ ] B1.1 DirectoryService  - [ ] B1.2 DirectoryController  - [ ] B1.3 MyScopedRoles  - [ ] B1.4 tests + OpenApi list  - [ ] Gate B1 + `api:generate`
- [ ] F1.1 deps  - [ ] F1.2 dates  - [ ] F1.3 labels  - [ ] F1.4 messages  - [ ] F1.5 registry/proxy  - [ ] F1.6 campus  - [ ] F1.7 timetable  - [ ] F1.8 routes  - [ ] F1.9 tests  - [ ] Gate F1
- [ ] Backend full `verify` green  - [ ] `build` green  - [ ] Exit check done  - [ ] `timetia-docs/modules/lecture-rescheduling/changelog.md` line added
