# API reference for the Timetia screens

Base path `/api/v1`. The browser calls the same paths on the web app's own origin (`/api/v1/...`); the Next.js proxy adds
the bearer token. Types below are TypeScript-style summaries of `backend/openapi.json`; in code always use
`components["schemas"]["<Name>"]` from `src/lib/api/schema.gen.ts`. `?` = may be absent or null.

Endpoints marked **NEW (Pn)** do not exist yet; phase *n* adds them (spec in `phase-n.md`). Fields marked **+Pn** are
added to an existing response by phase *n*.

## Conventions (ADR 0043)

- **IDs** are UUID strings. **Dates** `YYYY-MM-DD`. **Times** `HH:mm:ss` in responses (`"09:00:00"`); requests accept
  `"09:00"` or `"09:00:00"`. Always cut to `HH:mm` for display (`hhmm()` in `src/lib/dates.ts`). **Instants**
  (`createdAt`, `expiresAt`, `decidedAt`, `readAt`) are ISO-8601 UTC, e.g. `"2026-07-06T03:30:00Z"`.
- **Lists** are paged: query `page` (from 0), `size` (default 20, **maximum 100**), `sort=field,asc|desc` (allow-list per
  endpoint). Response: `{ items: T[], page: { number, size, totalElements, totalPages } }`. To read everything, request
  pages until `number + 1 >= totalPages`. Endpoints documented as "unpaged" return a plain array.
- **Errors** are RFC 9457 problems: `{ type?, title, status, code, detail?, traceId?, errors?: [{ field, code, message }] }`.
  `code` selects the user-facing text ([domain.md](domain.md), section 7). 400 with `errors` = validation; 401 = session
  ended; 403 = not allowed; 404 = not found or hidden; 409 = state/conflict; 429 = rate limited.
- **Writes** (`POST`, `PUT`, `DELETE`) need the CSRF header `x-timetia-request: 1`; `browserApi()` sends it.
- **Optimistic locking**: resources with `version` must send it back on `PUT`; a stale one → 409
  (`platform.concurrent_modification`, or `rescheduling.stale_ticket` for tickets).
- **Create** answers 201 with the resource and a `Location` header.

## 1. Who am I

### `GET /me` → `MeResponse` (exists)
```ts
{ id, username?, email?, emailVerified: boolean, name: string, status: "INVITED"|"ACTIVE"|"DISABLED",
  theme: "SYSTEM"|"LIGHT"|"DARK", permissions: string[], roles: { id, name, builtIn }[],   // roles: flat roles only
  avatarVersion?, avatarsEnabled: boolean, filesEnabled: boolean, administrator: boolean }
```
Loaded once per page by `PageShell`; read it with `usePermissions().me`.

### `GET /me/scoped-roles` → `ScopedRoleResponse[]` — **NEW (P1)**, unpaged
```ts
{ roleId, roleName: string, scopeType: "GLOBAL"|"DEPARTMENT"|"DIVISION"|"BATCH", scopeId?: string, scopeCode?: string }[]
```
The caller's own roles given in a place. Faculty and Super HOD appear with `scopeType: "GLOBAL"` and no `scopeId`.

### `GET /directory/me` → `MyCampusResponse` — **NEW (P1)**
```ts
{ faculty: { id, code, name, departmentId }[],              // the faculty record(s) linked to this account; usually 0 or 1
  assignments: { id, divisionId, divisionCode, batchId?, batchCode?, subjectId, subjectCode, subjectName,
                 facultyId, sessionType }[] }               // what those faculty teach
```

## 2. Campus directory (read-only, any signed-in person with a verified email) — **NEW (P1)**, all unpaged, sorted by code (faculty by name)

| Endpoint | Item shape |
|---|---|
| `GET /directory/departments` | `{ id, code, name }` |
| `GET /directory/divisions` | `{ id, code, name, departmentId }` |
| `GET /directory/batches?division=<id>` | `{ id, code, name, divisionId }` (`division` optional) |
| `GET /directory/rooms` | `{ id, code, name, roomType: "CLASSROOM"|"TUTORIAL_ROOM"|"LAB", capacity? }` |
| `GET /directory/faculties?department=<id>` | `{ id, code, name, departmentId, hasAccount: boolean }` (`department` optional) |

Each returns at most 500 rows. Used for pickers and to turn an id into a name.

## 3. Live timetable (exists)

### `GET /timetable/live` → page of `LiveLectureResponse`
Query: `from` and `to` (required, inclusive, at most 62 days apart: else 400 `to` → `range_too_long` / `before_from`),
`division?`, `batch?` (includes the theory lectures of the batch's division), `faculty?` (faculty id), paging,
`sort` ∈ `date`, `startTime` (default date then start time).
```ts
{ id, date, startTime, endTime, sessionType, mode: "IN_PERSON"|"ONLINE",
  divisionId, divisionCode?, batchId?, batchCode?, subjectCode?, subjectName?,
  facultyId, facultyName?,            // the CURRENT teacher (the substitute when there is one)
  roomId?, roomCode?, meetingLink?,
  cancelled: boolean,                 // shown, marked, so nobody goes
  changed: boolean,                   // an applied ticket is laid over it
  originalDate?, originalStartTime?, originalEndTime?,   // only when it was MOVED
  extra: boolean,                     // a ticket added it
  originalFacultyId? }                // only when a substitute teaches it
```
### `GET /timetable/live/{id}` → `LiveLectureResponse`; 404 `timetable.lecture_not_found` when missing or hidden.

### `GET /timetable/free-rooms` → `FreeRoomResponse[]` — **NEW (P2)**, unpaged
Query: `date`, `startTime`, `endTime` (required, on the hour, 08:00 to 20:00, start before end), `sessionType?`,
`excludeLecture?` (a lecture whose own room does not count as taken: the one being moved).
```ts
{ id, code, name, roomType, capacity?, fits: boolean }[]    // only rooms free for every hour asked; fits first, then by code
```
`fits`: no `sessionType` → true; `LAB` → room is `LAB`; `THEORY` → `CLASSROOM`; `TUTORIAL` → `TUTORIAL_ROOM` or `CLASSROOM`.
A room that does not fit is still offered (the server only suggests; the author picks).

## 4. Tickets (exist unless marked)

### `GET /tickets` → page of `TicketResponse`
Query: `status?` (`DRAFT|IN_REVIEW|APPLIED|REJECTED|WITHDRAWN|EXPIRED|REVERTED`), `lecture?` (lecture id), `mine?`
(boolean: only tickets I wrote), **`awaitingReview?` — NEW (P2)** (boolean: only tickets `IN_REVIEW` where I have a
pending review in the current revision), paging, `sort` ∈ `createdAt`, `expiresAt` (default newest first).
In a list `conflicts` and `reviews` are **always empty arrays**, and `change.lecture` is absent; `conflicted` is correct.

### `TicketResponse`
```ts
{ id, type: "RESCHEDULE"|"CANCEL"|"SUBSTITUTE"|"EXTRA"|"EXCHANGE"|"REVERT"|"PERMANENT_CHANGE",
  status, approvalPath?: "SELF"|"REVIEWED",
  authorUserId, authorName?: string /* +P2 */,
  actingFacultyId?,                    // set when an HOD raised it for this teacher
  description?, revisionNo: number,
  divisionId, batchId?, sessionType, facultyId,          // the lecture's place and teacher when raised
  subjectCode?, subjectName?, divisionCode?, batchCode?, facultyName?,   // +P2
  change: ChangeResponse,              // = changes[0]
  changes: ChangeResponse[],           // 1; 2 for EXCHANGE; many for PERMANENT_CHANGE and its REVERT
  conflicts: ConflictResponse[], conflicted: boolean,
  reviews: ReviewResponse[],           // every revision's, oldest first
  expiresAt, closedAt?, revertsTicketId?, revertedByTicketId?,
  version: number, createdAt, updatedAt }

ChangeResponse  { operation: "MOVE"|"CANCEL"|"REASSIGN_FACULTY"|"CREATE_EXTRA"|"RESTORE",
                  lectureId?,          // the lecture changed; for an extra, the one created once applied
                  assignmentId?,       // extra only
                  target?: Slot,       // where it goes (MOVE, CREATE_EXTRA, RESTORE)
                  reason?,
                  substituteFacultyId?, substituteFacultyName?: string /* +P2 */,
                  before?: Slot,       // where it was; present once APPLIED
                  beforeFacultyId?, beforeFacultyName?: string /* +P2 */,
                  lecture?: { date, startTime, endTime, mode, roomCode?, subjectCode?, facultyName? } /* +P2, detail only: the lecture as it is NOW */ }
Slot            { date, startTime, endTime, mode, roomId?, roomCode?: string /* +P2 */, meetingLink? }
ConflictResponse{ type, message, lectureId?, conflictingLectureId?, conflictingTicketId? }
ReviewResponse  { reviewerUserId, reviewerName?: string /* +P2 */, capacity: "HOD"|"CR"|"BR"|"AFFECTED_FACULTY"|"OTHER_FACULTY",
                  status: "PENDING"|"APPROVED"|"REJECTED"|"ACKNOWLEDGED", reason?, decidedAt?, revisionNo, escalation: boolean }
```

### Actions
| Call | Body | Success | Notable errors |
|---|---|---|---|
| `POST /tickets` | `CreateTicketRequest { type, lectureId?, otherLectureId?, assignmentId?, description?, target?: {date,startTime,endTime,mode,roomId?,meetingLink?}, reason?, substituteFacultyId? }` | 201 `TicketResponse` (status `DRAFT`, conflicts filled) | 400 field errors (domain.md 3.1); 403; 404 `rescheduling.lecture_not_found` / `assignment_not_found`; 409 `lecture_started`, `lecture_cancelled`, `not_weekly` |
| `GET /tickets/{id}` | | 200 | 404 `rescheduling.ticket_not_found` |
| `PUT /tickets/{id}` | `UpdateTicketRequest { description?, target?, reason?, substituteFacultyId?, reviewers?: {userId,capacity}[], version }` | 200 | 409 `ticket_not_editable`, `stale_ticket`; 400 |
| `POST /tickets/{id}/apply` | none | 200 (`APPLIED`) | 409 `blocked_by_conflicts` (+`errors`), `ticket_expired`, `reviews_pending`, `needs_hod_approval`, `ticket_not_draft`, `timetable.slot_taken`; 403 |
| `POST /tickets/{id}/submit` | `{ reviewers: {userId, capacity}[] }` | 200 (`IN_REVIEW`) | 400 `reviewers` errors; 409 `ticket_not_draft`, `ticket_expired`, `lecture_started` |
| `POST /tickets/{id}/review` | `{ decision: "APPROVE"|"REJECT"|"ACKNOWLEDGE", reason? }` | 200 (may now be `APPLIED` or `REJECTED`, or still `IN_REVIEW`) | 400 `reason`/`decision`; 403 not asked; 409 `ticket_not_in_review`, `review_already_decided`, `ticket_expired` |
| `POST /tickets/{id}/escalate` | `{ hodUserId }` | 200 (`IN_REVIEW`) | 409 `ticket_not_rejected`; 400 `reviewers` errors |
| `POST /tickets/{id}/withdraw` | none | 200 (`WITHDRAWN`) | 409 `ticket_not_draft` |
| `POST /tickets/{id}/revert` | `{ reason? }` (body optional) | **201 a new `REVERT` ticket** | 409 `ticket_not_applied`, `not_revertible`, `revert_exists`, `lecture_changed_since`, `lecture_started`, `original_slot_passed`; 403 |
| `GET /tickets/{id}/reviewer-candidates` — **NEW (P2)** | | 200 `{ userId, name, capacity }[]`, unpaged | 404; 403 when not the author |

## 5. Issues (exist)

`IssueResponse`
```ts
{ id, status: "OPEN"|"CLOSED", sessionType, departmentId, divisionId, batchId?, title, description?,
  openerUserId, openerName?: string /* +P3 */, divisionCode?: string /* +P3 */,
  mentionedFacultyIds: string[], mentionedFaculty?: { id, name }[] /* +P3 */,
  linkedTicketIds: string[], closedAt?, closedByUserId?, closedByName?: string /* +P3 */,
  version, createdAt, updatedAt }
```
| Call | Body | Notes |
|---|---|---|
| `GET /issues?status&mine` | | page; `sort` ∈ `createdAt` (default newest first) |
| `POST /issues` | `{ sessionType, divisionId?, batchId?, title, description?, mentionedFacultyIds?: string[] }` | 201 |
| `GET /issues/{id}` | | 404 `issues.issue_not_found` |
| `POST /issues/{id}/close` | none | 409 `issues.issue_not_open` |
| `POST /issues/{id}/mentions` | `{ facultyId }` | opener only (403 otherwise) |
| `POST /issues/{id}/links` | `{ ticketId }` | 404 `issues.ticket_not_found` |

## 6. Notifications (exist)

`NotificationResponse`
```ts
{ id, type: "REVIEW_REQUESTED"|"REVIEW_APPROVED"|"REVIEW_REJECTED"|"TICKET_APPLIED"|"TICKET_CONFLICTED"
            |"TICKET_EDITED"|"TICKET_WITHDRAWN"|"TICKET_REVERTED"|"ISSUE_MENTION",
  ticketId?, issueId?, actorUserId?,
  actorName?: string, ticketType?: string, lectureId?: string,    // +P3
  ticketVisible: boolean,   // +P3: may the caller open the ticket? Link by this, not by permissions
  createdAt, readAt? }
```
| Call | Returns |
|---|---|
| `GET /notifications?unread=true&page&size` | page, newest first |
| `GET /notifications/unread-count` | `{ count: number }` |
| `POST /notifications/{id}/read` | the notification, `readAt` set (idempotent) |
| `POST /notifications/read-all` | `{ marked: number }` |

## 7. Campus setup (exist; Super Admin, `timetable.config.manage`)

All eight follow one shape: `GET /<res>` (page; `q` text filter on code/name; `sort` ∈ `code`, `name`, `createdAt`),
`POST /<res>`, `GET /<res>/{id}`, `PUT /<res>/{id}` with `RenameRequest { name, version }` (**only the name can be
changed**), `DELETE /<res>/{id}` (204). Duplicate code → 409 `reference.duplicate_code`.

| Resource | Create body | Response adds | List filters |
|---|---|---|---|
| `/departments` | `{ code, name }` | | `q` |
| `/divisions` | `{ code, name, departmentId }` | `departmentId` | `department`, `q` |
| `/batches` | `{ code, name, divisionId }` | `divisionId` | `division`, `q` |
| `/subjects` | `{ code, name }` | | `q` |
| `/rooms` | `{ code, name, roomType, capacity? }` | `roomType`, `capacity?` | `q` |
| `/faculties` | `{ code, name, departmentId, userId? }` | `departmentId`, `userId?` | `department`, `q` |
| `/academic-terms` | `{ code, name, startDate, endDate }` (`endDate` → `end_before_start`) | `startDate`, `endDate` | `q` |
| `/teaching-assignments` | `{ divisionId, subjectId, facultyId, sessionType, batchId? }` (`THEORY`: no batch → `batch_not_allowed`; lab/tutorial: batch of that division → `batch_required`, `unknown_batch`); duplicate → 409 `reference.duplicate_teaching_assignment` | all ids, `sessionType` | `division`, `faculty`, `subject`; **no `PUT`** |

Every response also has `id`, `version`, `createdAt`, `updatedAt`.

`PUT /faculties/{id}/account` — **NEW (P3)**: body `{ userId: string | null, version }` → `FacultyResponse`. Links or
unlinks the sign-in account. `userId` → `unknown_user`.

Users for the account picker: `GET /users?q&state&page&size` → page of `UserResponse { id, username?, email?, name, state, ... }`
(admin, exists).

## 8. Timetable authoring (exist; Super Admin)

| Call | Body / query | Returns / errors |
|---|---|---|
| `GET /timetable/versions?division&status` | `status` = `DRAFT`\|`PUBLISHED`; page | `TimetableVersionResponse { id, divisionId, termId, effectiveFrom, status, version, createdAt, updatedAt }` |
| `POST /timetable/versions` | `{ divisionId, termId, effectiveFrom }` | 201; 400 `divisionId` → `unknown_division`, `termId` → `unknown_term`, `effectiveFrom` → `outside_term` |
| `GET /timetable/versions/{id}` / `DELETE` | | 404 `timetable.version_not_found`; deleting a published one → 409 `timetable.version_published` |
| `GET /timetable/versions/{id}/entries` | | page-shaped `{items, page}` holding **all** entries: `TimetableEntryResponse { id, versionId, assignmentId, dayOfWeek, startTime, endTime, roomId?, mode, meetingLink? }` |
| `POST /timetable/versions/{id}/entries` | `{ assignmentId, dayOfWeek (1-6), startTime, endTime, mode, roomId?, meetingLink? }` | 201; 400 `assignmentId` → `unknown_assignment`, `assignment_of_another_division`; `dayOfWeek` → `invalid_day`; `startTime` → `invalid_time`; `roomId` → `room_required`, `unknown_room`, `room_not_allowed`; `meetingLink` → `link_not_allowed`; 409 `timetable.clash` (clashes with an entry already in the version), `timetable.version_published` |
| `DELETE /timetable/versions/{id}/entries/{entryId}` | | 204; 409 `timetable.version_published` |
| `POST /timetable/versions/{id}/publish` | none | 200 `{ version, occurrencesCreated, occurrencesSuperseded }`; 409 `timetable.version_empty`, `effective_from_taken`, `reference_missing`, `clash` (the `detail` names what is booked twice: show `problem.detail` for this code), `changes_would_be_lost` |

There is no entry edit: delete and add again.
