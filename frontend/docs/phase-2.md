# Phase 2: tickets (the core of the project)

Read first: [plan.md](plan.md), **all** of [domain.md](domain.md) (section 3 is the spec of every screen's behaviour),
[api-reference.md](api-reference.md) sections 3 and 4, [architecture.md](architecture.md), [backend-guide.md](backend-guide.md).

## 0. Purpose and result

Make tickets usable end to end: raise any of the six kinds, see conflicts, apply or send for review, answer reviews, reject,
escalate, edit, withdraw and revert, with **names instead of ids**, a **reviewer picker**, and **free-room suggestions**.
**Result**: see plan.md section 4, "After Phase 2". The exit check is `scripts/DEMO.md` section 5 done by clicking.

## 1. Prerequisites for this phase

- [ ] Phase 1 is finished: all its checkboxes ticked, full backend `verify` green, `/timetable` works for `demo` and `demo-admin`.
- [ ] `frontend/src/features/campus` and `features/timetable` exist with `LectureSheet` accepting an `actions` prop.
- [ ] The demo campus is seeded and `demo` (Faculty), `demo-admin` (Faculty + HOD of CSE) can both sign in.
- [ ] You have read the full ticket service so the rules in domain.md section 3 are not a surprise:
      `backend/.../rescheduling/application/TicketService.java`, `TicketReviewService.java`, `TicketRevertService.java`.

## 2. What changes

| Area | Change |
|---|---|
| New endpoints | `GET /tickets/{id}/reviewer-candidates`, `GET /timetable/free-rooms`, query parameter `awaitingReview` on `GET /tickets` |
| Changed responses | `TicketResponse`, `ChangeResponse`, `TargetResponse`, `ReviewResponse` gain name fields (api-reference.md section 4, marked +P2) |
| New ports | `access.ScopeHolders.usersHolding(...)`; `reference.AcademicDirectory.roomOptions()` |
| New module edges | `rescheduling → access` (pin in `ModularityTest`) |
| Frontend | `features/tickets`, `lib/problems`, `lib/slot`, 3 routes, `modules.ts` tickets block, `LectureSheet` actions |

## 3. Backend tasks

### B2.1 Names on ticket responses

Goal: the UI never has to map ids to names for tickets. Add nullable fields (api-reference.md section 4 lists them). A name is
`null` when its record was deleted. Do not change existing field names or types.

1. Create `rescheduling/application/TicketNames.java`:
   ```java
   public record TicketNames(Map<UUID, String> userNames, Map<UUID, FacultyDetails> faculty, Map<UUID, String> roomCodes,
                             Map<UUID, AssignmentDetails> assignments, Map<UUID, LectureView> lectures) {
       public static final TicketNames NONE = new TicketNames(Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
   }
   ```
2. Create `rescheduling/application/TicketNameService.java`, `@Service`, one public method
   ```java
   @Transactional(readOnly = true)
   @NoAuthorization("adds names to tickets the caller has already been allowed to see")
   public TicketNames namesFor(Collection<TicketDetail> details, boolean withLectures)
   ```
   It collects, from every detail: `authorUserId`, `reviews[].reviewerUserId` → `UserDirectory.find(ids)` (`displayName`);
   `ticket.lecture().facultyId()`, `change.facultyId()`, `substituteFacultyId()`, `beforeFacultyId()` → `AcademicDirectory.faculties(ids)`;
   `target.roomId()`, `before.roomId()` → `AcademicDirectory.rooms(ids)` (code); `ticket.lecture().assignmentId()` →
   `AcademicDirectory.assignments(ids)`; and, when `withLectures`, every `change.occurrenceId()` → `LectureCalendar.lecture(id)`.
   **One lookup per kind for the whole page**, never one per row. `UserDirectory` is `platform.directory.UserDirectory`
   (inject it; `TicketSupport` already does).
3. `TicketsController`: inject `TicketNameService`; every place that builds a `TicketResponse` passes names:
   `TicketMapper.toResponse(detail, names)`; the list passes `namesFor(page items, false)`, single-ticket answers pass
   `namesFor(List.of(detail), true)`. Keep a private helper `respond(TicketDetail)` in the controller.
4. Response records: add to `TicketResponse`: `authorName`, `subjectCode`, `subjectName`, `divisionCode`, `batchCode`,
   `facultyName` (all `@Schema(nullable = true)`); `ChangeResponse`: `substituteFacultyName`, `beforeFacultyName`,
   `lecture` (new record `LectureSummaryResponse(LocalDate date, LocalTime startTime, LocalTime endTime, LectureMode mode,
   String roomCode, String subjectCode, String facultyName)`, nullable, filled only when `withLectures` and the lecture exists
   and the caller can see it: `namesFor` runs after the ticket was authorized; the lecture belongs to the same ticket, so show it);
   `TargetResponse`: `roomCode`; `ReviewResponse`: `reviewerName`. Update `TicketMapper` (hand-written).
5. Tests (`rescheduling/TicketNamesApiIT extends ReschedulingApiTest`): raise a reschedule as Rao; `GET /tickets/{id}` has
   `authorName` "Given xxxx" (the fake identity provider's display name pattern: assert `isNotEmpty`), `facultyName` "Dr Rao",
   `subjectCode` "OOP", `divisionCode` "CSE-A", `change.lecture.roomCode` "R101", `change.target.roomCode` the target room;
   a substitute ticket has `change.substituteFacultyName` "Dr Roy"; after apply `change.before.roomCode` is set; a deleted room
   (`DELETE /api/v1/rooms/{id}` as admin) makes `roomCode` null and does not break the response; the list response has the
   same top-level names and `change.lecture` absent.

### B2.2 Reviewer candidates

`GET /api/v1/tickets/{id}/reviewer-candidates` → `[{ userId, name, capacity }]` (api-reference.md section 4).

1. `access/ScopeHolders.java` (published port): add
   ```java
   /** Like usersIn, but only people whose role in the place carries this permission. */
   Map<UUID, Set<UUID>> usersHolding(String scopeType, Collection<UUID> scopeIds, String permission);
   ```
   Implement in `access/persistence/StoredScopeHolders.java` by adding to the existing query
   `join role_permission rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id and rp.permission = :permission`
   (the same table `StoredPermissionSource` reads). Contract test: add a case to the existing scope-holder test if one exists, else cover it through the IT below.
2. `rescheduling/application/ReviewerCandidateService.java`, `@Service`:
   ```java
   public record Candidate(UUID userId, String name, ReviewerCapacity capacity) {}
   @Transactional(readOnly = true)
   public List<Candidate> candidates(UUID ticketId)
   ```
   Steps: `ticket = support.load(id)`; `facts = support.facts(ticket)`; `support.requireSeen(facts)`;
   `authorization.require(AcademicPermissions.TICKETS_EDIT_OWN, facts)` (a **direct** call so the ArchUnit rule is met; only the
   author). Then:
   - HOD: `holders.usersHolding(DEPARTMENT, Set.of(ticket.lecture().departmentId()), AcademicPermissions.TICKETS_APPROVE_PERMANENT)` → capacity `HOD`.
   - THEORY ticket: `holders.usersHolding(DIVISION, Set.of(divisionId), TICKETS_REVIEW)` → `CR`.
   - LAB/TUTORIAL ticket with `batchId`: `holders.usersHolding(BATCH, Set.of(batchId), TICKETS_REVIEW)` → `BR`.
   - `facts.affectedFacultyUserIds()` → `AFFECTED_FACULTY`.
   Remove the author and duplicates (a person appears once per capacity); look up names with `UserDirectory.find`, drop people whose
   `state` is not `ACTIVE` (`UserSummary.active()`); sort by capacity order (HOD, CR, BR, AFFECTED_FACULTY) then name.
   Super HODs (a global role) and "other faculty anywhere" are not listed: documented limitation (they can be named only via the API).
3. Controller: add `GET /{id}/reviewer-candidates` to `TicketsController` returning a response record `ReviewerCandidateResponse(userId, name, capacity)`.
4. `ModularityTest`: add `"access"` to the `.contains(...)` list for `rescheduling` (new edge `rescheduling → access`). The reverse edge must not exist (it does not).
5. Tests (`rescheduling/ReviewerCandidatesApiIT extends ReschedulingApiTest`): give `personWith("HOD", deptCse)`, `personWith("CR", divisionCseA)`,
   `personWith("BR", batchA1)`, `personWith("Student", batchA1)`, `personWith("HOD", deptMe)`. For a THEORY reschedule by Rao: candidates = the CSE HOD (capacity HOD) and
   the CR; **not** the BR, the Student or the ME HOD; for a LAB (A1) ticket: HOD and the A1 BR, not the CR; for an exchange with Roy: Roy appears as
   `AFFECTED_FACULTY`; the author never appears; as another teacher → 404 (cannot see) and as the HOD → 403 (sees it, is not the author).

### B2.3 `awaitingReview` filter

1. `TicketsController.list`: new `@RequestParam(defaultValue = "false") boolean awaitingReview`; `TicketFilter` gets a fourth component `awaitingReview`.
2. `TicketService.list`: when `awaitingReview`: if `filter.status()` is set and is not `IN_REVIEW` return an empty page; otherwise use
   `new Visibility(false, Set.of(), Set.of(), Set.of(), null, Set.of(), me)` (only the `reviewing` branch) and status `IN_REVIEW`.
3. `TicketQuery` gets `boolean pendingReviewOnly`; `TicketSpecifications` (the `reviewing` subquery near line 74) adds
   `builder.equal(review.get("status"), ReviewStatus.PENDING)` when it is true. Update every `new TicketQuery(...)` call site
   (`TicketService.java` line ~593 and tests: grep).
4. Test in `TicketReviewApiIT` or a new `AwaitingReviewApiIT`: Rao asks Roy to review; `GET /tickets?awaitingReview=true` as Roy lists it; after Roy approves
   (or if the ticket is in an earlier revision) it no longer appears; as Rao (the author) the list is empty; the `mine=true` list for Rao still has it.

### B2.4 Free rooms

`GET /api/v1/timetable/free-rooms?date&startTime&endTime&sessionType&excludeLecture` → `[{id, code, name, roomType, capacity, fits}]`.

1. `reference/AcademicDirectory`: add `List<RoomOption> roomOptions()` and a published record `reference/RoomOption.java`
   `(UUID id, String code, String name, String roomType, Integer capacity)` (`roomType` is the enum name as a string; `RoomType` is not published).
   Implement in `AcademicDirectoryService` via `catalog.searchRooms(null, PageRequest.of(0, 500, Sort.by("code")))`.
2. `timetable/application/RoomAvailabilityService.java`, `@Service`, `@NoAuthorization("room occupancy is already visible to anyone who sees the timetable")`
   with `authorization.requireVerified()` first:
   ```java
   public record FreeRoom(UUID id, String code, String name, String roomType, Integer capacity, boolean fits) {}
   public List<FreeRoom> freeRooms(LocalDate date, LocalTime start, LocalTime end, SessionType sessionType, UUID excludeLecture)
   ```
   Validate: date not a Sunday (`date` → `sunday`), start/end on the hour, `08:00 ≤ start < end ≤ 20:00` (`startTime`/`endTime` → `invalid_time`; reuse the messages of
   `TimetableAuthoringService` line ~326) → `ApiException.invalid(VALIDATION_FAILED, ..., errors)`. Build `ClaimKey(ClaimKind.ROOM, room.id(), date, hour)` for each room and each hour in `[start, end)`; `holders = claims.holders(keys)`;
   a room is free when for every hour there is no holder, or the holder equals `excludeLecture`. `fits`: `sessionType == null` → true; `LAB` → roomType `LAB`; `THEORY` → `CLASSROOM`; `TUTORIAL` → `TUTORIAL_ROOM` or `CLASSROOM`.
   Order: fits first, then code.
3. `timetable/web/FreeRoomsController.java` (`/api/v1/timetable/free-rooms`) with `@DateTimeFormat(iso = DATE)` on `date`, `@DateTimeFormat(iso = TIME)` on times (the same pattern as `LiveTimetableController`), and a response record `FreeRoomResponse`.
4. Tests (`timetable/FreeRoomsApiIT extends ReschedulingApiTest`): Monday 2026-07-13 09:00–10:00 → all four rooms free; Monday 2026-07-06 09:00–10:00 → R101 and R201 missing (held by OOP and Thermodynamics) and R102, LAB1 present; with `excludeLecture` = the OOP lecture R101 is present; `sessionType=LAB` → LAB1 `fits:true` listed first, classrooms `fits:false`; a 2-hour window where one of the two hours is held excludes the room; Sunday → 400; `startTime=09:30` → 400; unverified → 403.

### B2.5 Registration and gate

Add to `OpenApiContractIT` list: `/api/v1/tickets/{id}/reviewer-candidates`, `/api/v1/timetable/free-rooms`.
**Gate B2**: run `TicketNamesApiIT`, `ReviewerCandidatesApiIT`, `AwaitingReviewApiIT`, `FreeRoomsApiIT`, then **all existing `rescheduling.*` ITs**
(`TicketApiIT`, `TicketReviewApiIT`, `TicketVisibilityApiIT`, `ExchangeApiIT`, `ExtraLectureApiIT`, `PermanentChangeApiIT`, `RevertApiIT`, `SubstituteApiIT`: they must still pass),
`ModularityTest`, `ArchitectureTest`, `OpenApiContractIT`; then `api:generate`.

## 4. Frontend tasks

### F2.1 Registry

`modules.ts`: add the `tickets` block: `nav: { href: "/tickets", label: "Tickets", permissions: ["rescheduling.tickets.view", "rescheduling.tickets.view-in-scope"] }`,
`protectedPrefixes: ["/tickets"]`; `proxy.ts` matcher gets `"/tickets", "/tickets/:path*", // module:tickets`. Update `modules.test.ts` if it enumerates.

### F2.2 `features/tickets/api.ts`

Hooks (architecture.md 3.1): `useTickets(params)` (params `{page,size,status?,mine?,awaitingReview?,lecture?,sort?}`), `useTicket(id)`,
`useReviewerCandidates(ticketId, enabled)`, `useFreeRooms(params | null)` (enabled when all of date/start/end are valid; `staleTime: 10_000`),
mutations `useCreateTicket()`, `useEditTicket(id)`, `useApplyTicket(id)`, `useSubmitTicket(id)`, `useReviewTicket(id)`, `useEscalateTicket(id)`,
`useWithdrawTicket(id)`, `useRevertTicket(id)`; all invalidate per the matrix in architecture.md section 6. Export types
`Ticket`, `TicketChange = Ticket["change"]`, `Review = Ticket["reviews"][number]`.

### F2.3 Pure logic (each with a table-driven unit test)

`lib/actions.ts`: `availableActions(ticket, me): { edit; apply; submit; withdraw; review: { decisions: ("APPROVE"|"REJECT"|"ACKNOWLEDGE")[] } | null; escalate; revert; primary }`
implementing the pseudocode in domain.md section 3.4 exactly. `primary` is one of `"apply" | "submit" | "review" | "edit" | null` per the rule after the pseudocode.
Test table (rows = status × role × type, at least 30 cases): DRAFT author RESCHEDULE → apply+submit+edit+withdraw, primary apply; DRAFT author on behalf (actingFacultyId set) RESCHEDULE → no apply, primary submit;
DRAFT CANCEL on behalf → apply; DRAFT PERMANENT_CHANGE → no apply, no edit, submit, withdraw; IN_REVIEW author with reviews all approved → apply; with one pending → no apply; PERMANENT_CHANGE approved by a CR only → no apply, approved by HOD → apply;
IN_REVIEW non-author with my pending review → review decisions [APPROVE, REJECT, ACKNOWLEDGE]; same for a permanent change with capacity HOD → [APPROVE, REJECT]; REJECTED author → edit+escalate (+withdraw? no: withdraw only DRAFT/IN_REVIEW); APPLIED RESCHEDULE with `revert` permission and I am anyone → revert true; with only `revert-in-scope`; APPLIED PERMANENT_CHANGE with only `revert` → false; APPLIED EXTRA → false; APPLIED with `revertedByTicketId` → false; REVERT ticket DRAFT author → apply; WITHDRAWN/EXPIRED/REVERTED → nothing.
Only reviews with `revisionNo === ticket.revisionNo` count.

`lib/slot.ts`: `toTarget(values: SlotValues): TargetRequest` (hours → `"HH:00"`, drops `roomId` for ONLINE and `meetingLink` for IN_PERSON, trims, empty link → omitted); `lectureHours(l)`; `HOURS = [8..19]`. Test.
`lib/problems.ts`: `remap(error)` per architecture.md 3.3; `conflictsOf(error): {type, message}[]` returns `error.problem.errors` mapped when `error.code === "rescheduling.blocked_by_conflicts"` (the `code` of each field error is the conflict type lower-cased: map back with `.toUpperCase()`), else `[]`. Test both.
`lib/describe.ts`: `describeChange(change, ticket)`: returns `{ headline: string, from?: string, to?: string }`:
MOVE → from = `slotText(change.before ?? change.lecture)` ("Mon 6 Jul · 09:00–10:00 · R101" or "· Online"), to = `slotText(change.target)`;
CANCEL → headline "Cancel {subject} on {date}", detail = reason; REASSIGN_FACULTY → from = `beforeFacultyName ?? facultyName`, to = `substituteFacultyName`; CREATE_EXTRA → headline "Add {subject}", to = slot;
RESTORE → "Put back", from = lecture now, to = target. `slotText` uses `formatDay`, `hhmm`, `roomCode ?? (mode === "ONLINE" ? "Online" : "—")`. Test each operation including null names.
`schemas.ts`: `slotSchema` (architecture.md 3.3) plus `rescheduleSchema = slotSchema.extend({ description: z.string().max(1000).optional(), reason: z.string().max(500).optional() })`,
`cancelSchema { reason: z.string().trim().min(1, "Say why").max(500), description? }`, `substituteSchema { substituteFacultyId: z.string().min(1, "Choose a teacher"), reason?, description? }`,
`exchangeSchema { otherLectureId: z.string().min(1, "Choose a lecture"), description? }`, `permanentSchema = rescheduleSchema`, `extraSchema = slotSchema.extend({ assignmentId: z.string().min(1,"Choose a subject"), description? })`,
`reviewSchema { decision, reason? }` with refine "Say why" when REJECT, `submitSchema { reviewerIds: z.array(z.string()).min(1, "Choose at least one person") }`. Test the refinements.

### F2.4 Components (all in `features/tickets/components/`, presentational)

| Component | Spec |
|---|---|
| `StatusBadge` | `Badge` with `TICKET_STATUS_LABEL[status]` and `TICKET_STATUS_VARIANT[status]`; when `conflicted` and status is DRAFT/IN_REVIEW render a second destructive `Badge` "Conflict" |
| `ChangeSummary` | for each change (`describeChange`): headline, then a two-line "from → to" block (stack on narrow screens, side by side from `sm`). More than 5 changes → first 3 + `<details><summary>and N more lectures</summary>` listing the rest |
| `ConflictList` | props `{ conflicts: {type: ConflictType, message: string, conflictingLectureId?: string, conflictingTicketId?: string}[] }`; `<Alert variant="destructive">` titled "Something is in the way", `<ul>` of `CONFLICT_LABEL[type]` bold + `message`; when `conflictingLectureId`, a link "See that lecture" → `/timetable?lecture=<id>`; when `conflictingTicketId`, "See that ticket" → `/tickets/<id>`. Labels from domain.md section 3.5 (add `CONFLICT_LABEL` to `lib/labels.ts`) |
| `ReviewList` | table "Reviews": revision filter (default current revision; earlier revisions in a `<details>` "Earlier rounds"), columns Person (`reviewerName ?? "—"`), Role (`CAPACITY_LABEL`), Answer (`REVIEW_STATUS_LABEL`, escalation → "(escalated)"), Reason, When (`toLocaleString`) |
| `ActionBar` | takes `ticket`, `me` → `availableActions`; renders only available buttons: primary one filled (`Button`), the rest `variant="outline"`; destructive ones (Withdraw) `variant="outline"` with an `AlertDialog` confirmation; Apply is disabled with `title` "Resolve the conflicts first" when `ticket.conflicted`; shows nothing but a muted line when no action applies |
| `ReviewerPicker` | props `{ candidates, value: string[], onChange, requireHod }`: groups by capacity (`<fieldset><legend>`), each person a `Checkbox` + label; when `requireHod` and no HOD candidate exists, shows `<Alert>` "No HOD is available for this department. Ask an administrator." and the submit stays disabled |
| `ReviewDialog` | `Dialog` with a native radio group (`<fieldset>` of `<label><input type="radio">`; no new shadcn component); shows only `decisions` from `availableActions`; Reject reveals a required `Textarea`; on submit calls `useReviewTicket`; shows the resulting status in a toast ("Approved", "Ticket applied", "Rejected") |
| `RaiseDialog` | see F2.5 |
| `SlotFields` | RHF `Controller`s: Date (`<Input type="date" min={today}>`), Start hour (`Select` 08:00 to 19:00), End hour (read-only when `lockedHours` is given: shows start + hours; else `Select`), Mode (`Select`), Room (`Select` fed by `useFreeRooms`; options "R101 · Classroom · 60 seats"; not-fitting ones suffixed " (not a lab room)" etc.; loading → disabled "Looking for free rooms…"; none → "No free room at that time"), Meeting link (`Input`, ONLINE only) |
| `RevertDialog` | `AlertDialog` with optional reason `Textarea`; on success `router.push('/tickets/' + newId)` |

### F2.5 `RaiseDialog` and the forms

Props `{ kind: "RESCHEDULE"|"CANCEL"|"SUBSTITUTE"|"EXCHANGE"|"PERMANENT_CHANGE", lecture: Lecture, open, onOpenChange }`. One RHF form per kind (schemas in F2.3).
Submit: `useCreateTicket().mutateAsync(body)` with the body built exactly as domain.md section 3.1 requires (fields not listed for the type are **not sent**):

| Kind | Body |
|---|---|
| RESCHEDULE | `{ type, lectureId, description?, reason?, target: toTarget(values) }` |
| CANCEL | `{ type, lectureId, reason, description? }` |
| SUBSTITUTE | `{ type, lectureId, substituteFacultyId, reason?, description? }` |
| EXCHANGE | `{ type, lectureId, otherLectureId, description? }` |
| PERMANENT_CHANGE | `{ type, lectureId, description?, reason?, target: toTarget(values) }` |
| EXTRA (from `/tickets/new`) | `{ type: "EXTRA", assignmentId, description?, target: toTarget(values) }` |

The RESCHEDULE and PERMANENT_CHANGE forms lock the end hour to `startHour + lectureHours(lecture)` (the server rejects a different length) and default the date/hour/mode/room
to the lecture's current values; the date input shows the lecture's weekday in a helper line. Exchange: candidate list = `useLiveRange` (Phase 1, `features/timetable/api.ts`) for `division = lecture.divisionId`,
`from = today`, `to = today + 60 days`, filtered client-side to `sessionType` equal, `batchId` equal, `facultyId !== lecture.facultyId`, not cancelled, not the same lecture, in the future; each option
"Mon 13 Jul 09:00–10:00 · OOP · Dr Roy". Substitute: `useFaculties()` minus the current teacher, option text "{name} ({code})".
Permanent change: an `Alert` above the form: "This moves this lecture and every later {weekday} lecture of {subject} by the same amount. An HOD must approve it."
On success: `toast.success("Ticket created")`, close, `router.push('/tickets/' + created.id)`. On failure: `remap` + `applyProblem` for field errors; anything left → `ErrorAlert` in the dialog.
Note: a 201 with `conflicted: true` is a **success**; the conflicts are shown on the ticket page.

`LectureSheet` actions (built in `app/timetable/page.tsx` or a small `features/tickets/components/LectureActions.tsx` that the page passes in as `actions`): a `DropdownMenu` "Change this lecture" with the five kinds. Show an item when (hints, server decides):
the lecture is not cancelled; its start (`date` + `startTime`, browser local time) is in the future; and `has("rescheduling.tickets.create-on-behalf")` **or** (`has("rescheduling.tickets.create")` and `myCampus.facultyIds` includes `lecture.facultyId`);
Cancel uses the `cancel` / `cancel-on-behalf` permissions instead; Exchange is hidden unless the person teaches the lecture or is an HOD; Permanent change is hidden for an `extra` lecture. When no item is available render nothing.

### F2.6 Screens and routes

`TicketsScreen` (`/tickets`): shadcn `Tabs` Mine / To review / All I can see (store `tab`; the default is "Mine" if the person holds `rescheduling.tickets.create`, else "All I can see"); a status `Select` (All + each status); `Table`
(Type, Lecture "OOP · CSE-A · Mon 6 Jul" from `subjectCode`, `divisionCode`, and the first change's target/before date, People "by {authorName}", Status `StatusBadge`, Updated, link on the first cell → `/tickets/{id}`); `Pager`; empty text per tab ("You have not raised any tickets", "Nothing is waiting for your review", "No tickets yet").
Query mapping: Mine → `mine=true`; To review → `awaitingReview=true`; All → none; always `sort=createdAt,desc`, `size=20`. A "New extra lecture" button for people who hold `rescheduling.tickets.create` → `/tickets/new`.

`TicketDetailScreen` (`/tickets/[id]`), vertical order: back link; `h1` "{TICKET_TYPE_LABEL} · {subjectName ?? "Lecture"} · {divisionCode}{ · batchCode}"; `StatusBadge`; meta line (raised by `authorName` — "for {facultyName}" when `actingFacultyId`
is set — created time, "Expires {expiresAt} (when the earliest lecture starts)", revision when > 1); description; `ChangeSummary`; if `conflicts.length` → `ConflictList`; `ReviewList` when reviews exist; links "Reverts ticket" / "Reverted by ticket" when set;
`ActionBar`. Actions open: Edit → `EditTicketDialog` (reuses the same forms prefilled from `change.target`/`reason`; sends `version`; EXCHANGE edit shows only description; if the server answers 409 `rescheduling.stale_ticket`
show the message and a "Reload" button that refetches); Send for review → `SubmitDialog` with `ReviewerPicker` (calls `useReviewerCandidates`; for PERMANENT_CHANGE `requireHod`); Apply → mutation, success toast "Ticket applied", on 409 `rescheduling.blocked_by_conflicts` show
`ConflictList` from `conflictsOf(error)` in an `ErrorAlert` area above the buttons and refetch the ticket (its stored conflicts update); Withdraw → confirm; Escalate → dialog with the HOD candidates as single choice (`useReviewerCandidates`, capacity HOD only; none → message); Review → `ReviewDialog`; Revert → `RevertDialog`.
After a status change the page refetches; a toast names the new status.

`NewTicketScreen` (`/tickets/new`): extra lecture only: a `Select` of `myCampus.assignments` ("OOP · Theory · CSE-A", "OOP · Lab · CSE-A1"), `SlotFields` (end hour free to choose), description; no assignments → "You do not teach any subject yet.". Route page + the dynamic-route form in architecture.md 3.4.

### F2.7 Wire the lecture sheet

`app/timetable/page.tsx` passes `actions={<LectureActions lecture=... />}` into `TimetableScreen`/`LectureSheet` (the timetable feature must not import the tickets feature: it exposes the slot, the page composes).

### F2.8 Unit tests (stubApi + renderScreen)

1. `tickets-actions.test.ts` (F2.3), `ticket-schemas.test.ts`, `ticket-describe.test.ts`, `ticket-problems.test.ts`, `ticket-slot.test.ts`.
2. `tickets-screen.test.tsx`: each tab sends the right query (`mine=true` / `awaitingReview=true` / none), status filter adds `status=`, row link `/tickets/<id>`, empty text per tab, 500 → ErrorAlert.
3. `ticket-detail.test.tsx`: as the author of a DRAFT reschedule the bar shows Apply (primary), Send for review, Edit, Withdraw; as an HOD on behalf no Apply; as a reviewer with a pending review → Review; as a stranger-with-view-in-scope nothing; applying with a 409 blocked answer renders the `ConflictList` (labels from `errors[].code`) and leaves status unchanged; 404 shows "could not be found".
4. `raise-dialog.test.tsx`: RESCHEDULE sends `{type:"RESCHEDULE", lectureId, target:{date, startTime:"09:00", endTime:"10:00", mode:"IN_PERSON", roomId}}` and no `reason` key when empty; CANCEL requires a reason (button blocked, message "Say why"), sends no `target`; SUBSTITUTE excludes the current teacher from the options; EXCHANGE lists only same-session, same-batch, other-teacher lectures; a 400 with `errors:[{field:"target.roomId",code:"room_required",...}]` shows the message under the Room field; a 201 navigates (mock `next/navigation` `useRouter`).
5. `reviewer-picker.test.tsx`: groups by capacity, disables submit without selection and, for a permanent change, without an HOD.
6. `review-dialog.test.tsx`: Reject requires a reason; the permanent-change HOD sees no Acknowledge.

**Gate F2**: `typecheck`, `lint`, `test`, `build`.

## 5. Exit check (browser, two profiles: **demo** and **demo-admin**)

Follow `scripts/DEMO.md` section 5, but by clicking, and record each result:

1. `demo` opens `/timetable`, clicks the Monday OOP theory lecture → "Change this lecture" → Reschedule to Thursday 09:00–10:00: the room select lists free rooms (R101 is free on Thursday); create → ticket page: `DRAFT`, no conflicts, **Apply** is the primary button; Apply → `Applied`; `/timetable` shows the lecture on Thursday with "Moved".
2. Reschedule the DBMS lecture into the OOP slot (Monday 9–10) → the ticket shows `Conflict` with "Room is taken" and "Division is busy"; Apply is disabled; Edit to another time → conflict disappears; Apply works.
3. Review: `demo` raises a reschedule, **Send for review** → picker lists "Demo Administrator (HOD)" → submit → `In review`. `demo-admin` opens `/tickets` → tab **To review** shows it; opens it → Review → Approve → the ticket becomes `Applied` (approval path "reviewed") and `demo`'s page shows it.
4. Rejection: repeat, `demo-admin` rejects with a reason → `demo` sees `Rejected` + reason; Edit → back to `Draft`, revision 2; Send for review again; and separately **Escalate** a rejected one to the HOD.
5. Cancel (reason required), Substitute (Dr Admin takes OOP; the timetable shows "Substitute"), Extra lecture from `/tickets/new`, Exchange (Dr Demo's OOP with Dr Admin's DBMS), Permanent change (sent for review to the HOD; Apply only after the HOD approves; the whole series moves).
6. Revert an applied reschedule: new `Draft` revert ticket → Apply → the lecture is back; the original ticket shows `Reverted`.
7. As `demo-admin` (HOD): "Change this lecture" on `demo`'s lecture offers Reschedule/Cancel (on behalf); the created ticket has no Apply (send for review), Cancel has Apply.
8. A stranger (Student) opens `/tickets/<id>` → "could not be found".
9. 400 px width: tickets list scrolls inside its table container, ticket page stacks, dialogs fit.

## 6. Checklist

- [x] B2.1 names  - [x] B2.2 candidates (+ModularityTest edge)  - [x] B2.3 awaitingReview  - [x] B2.4 free rooms  - [x] B2.5 OpenApi list, Gate B2, `api:generate`
- [x] F2.1 registry  - [x] F2.2 api  - [x] F2.3 pure logic + tests  - [x] F2.4 components  - [x] F2.5 raise forms  - [x] F2.6 screens/routes  - [x] F2.7 sheet wiring  - [ ] F2.8 tests (only the pure-logic tests exist: `tickets-actions`, `tickets-lib`; the screen, dialog and picker component tests are not written)  - [x] Gate F2 (typecheck, lint, 302 unit tests pass, build)
- [ ] Backend full `verify` green (Gate B2 ITs were run, 118 integration + 14 unit tests green; the full suite was not)  - [x] `build` green  - [ ] Exit check 1 to 9 done (not run in a browser)  - [ ] changelog line added
