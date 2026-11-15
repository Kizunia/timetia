# Backend audit (2026-11-16)

Audit of the lecture-rescheduling backend before building the screens. State: complete and committed in `88a8b14`;
245 unit + 527 integration tests passed at the last full run. All Timetia endpoints are in `backend/openapi.json` and
typed in `src/lib/api/schema.gen.ts`.

Verdict: **the core flows are all built and correct. The API cannot yet serve a non-admin UI** because of blockers
B1 to B5 below. They are fixed inside the three phases ([plan.md](plan.md)).

## 1. Roles (seeded by `academic/application/RoleTemplateSeeder.java`)

| Role | Scope | Can, in UI terms |
|---|---|---|
| Super Admin (built-in Administrator) | global | Setup (`timetable.config.manage`), sees every lecture (`live.view-all`). **No ticket powers on its own.** |
| Super HOD | global | HOD powers across every department |
| HOD | department | Sees department lectures, raises tickets on behalf, reviews, approves permanent changes, reverts in scope |
| Faculty | global | Sees own lectures + lectures of divisions they teach in; raises/cancels/self-approves own tickets; reviews when asked |
| CR | division | Sees division; reviews theory tickets; opens/closes issues for theory |
| BR | batch | Sees batch; reviews lab/tutorial tickets; opens/closes issues for lab/tutorial |
| Student | batch | Reads the live timetable of their batch only |

`GET /api/v1/me` returns `permissions[]` (flat and scoped-role permissions) and `administrator`. Use permission names
from `academic/AcademicPermissions.java`: `timetable.live.view`, `.view-own`, `.view-all`, `.view-division`,
`timetable.config.manage`, `rescheduling.tickets.{create,create-on-behalf,cancel,cancel-on-behalf,self-approve,review,reject,approve-permanent,revert,revert-in-scope,edit-own,view,view-in-scope}`,
`rescheduling.issues.{open,close,view,view-in-scope}`.

## 2. Endpoint map by screen

| Screen | Endpoints | Who |
|---|---|---|
| Live timetable | `GET /api/v1/timetable/live?from&to&division&batch&faculty&page&size&sort` (max 62 days), `GET /api/v1/timetable/live/{id}` | all roles; the API filters by role and place |
| Ticket list | `GET /api/v1/tickets?status&lecture&mine` | everyone who may see tickets |
| Ticket detail | `GET /api/v1/tickets/{id}` | |
| Raise | `POST /api/v1/tickets` `{type, lectureId, otherLectureId, assignmentId, substituteFacultyId, target{date,startTime,endTime,mode,roomId,meetingLink}, reason, description}`; `type` = RESCHEDULE, CANCEL, SUBSTITUTE, EXTRA, EXCHANGE, PERMANENT_CHANGE (REVERT is created by the revert action) | Faculty, HOD on behalf |
| Edit | `PUT /api/v1/tickets/{id}` with current `version` | author |
| Apply / withdraw | `POST /api/v1/tickets/{id}/apply`, `/withdraw` | author |
| Send for review | `POST /api/v1/tickets/{id}/submit` `{reviewers:[{userId,capacity}]}` capacity = HOD, CR, BR, AFFECTED_FACULTY, OTHER_FACULTY | author |
| Review | `POST /api/v1/tickets/{id}/review` `{decision: APPROVE\|REJECT\|ACKNOWLEDGE, reason}` | requested reviewer |
| Escalate | `POST /api/v1/tickets/{id}/escalate` `{hodUserId}` (rejected ticket only) | author |
| Revert | `POST /api/v1/tickets/{id}/revert` `{reason?}` → new draft REVERT ticket, apply it | author, affected, HOD in scope |
| Issues | `GET/POST /api/v1/issues`, `GET /{id}`, `POST /{id}/close`, `/{id}/mentions {facultyId}`, `/{id}/links {ticketId}` | CR, BR, HOD, mentioned faculty |
| Notifications | `GET /api/v1/notifications?unread`, `/unread-count`, `POST /{id}/read`, `/read-all` | everyone, own only |
| Campus lookups (**Super Admin only today, see B1**) | `GET /api/v1/{departments,divisions,batches,subjects,rooms,faculties,academic-terms,teaching-assignments}` | `timetable.config.manage` |
| Campus setup | the same paths: `POST`, `PUT /{id}` (rename, with `version`), `DELETE /{id}` | Super Admin |
| Timetable authoring | `GET/POST /api/v1/timetable/versions`, `GET/DELETE /{id}`, `GET/POST /{id}/entries`, `GET/DELETE /{id}/entries/{entryId}`, `POST /{id}/publish` | Super Admin |
| Users (admin) | `GET /api/v1/users`, scoped roles `GET/POST/PUT/DELETE /api/v1/users/{userId}/scoped-roles` | admin |
| Audit | `GET /api/v1/audit-entries` | `audit.entries.view` |

Paged responses are `{items, page:{number,size,totalElements,totalPages}}`; `page` starts at 0.

## 3. Behaviour the UI must respect

- **Ticket types and what they carry**: a ticket has `change` (first) and `changes` (all). Each change has `operation`
  (MOVE, CANCEL, REASSIGN_FACULTY, CREATE_EXTRA, RESTORE), `before` and `target` slots, `substituteFacultyId`,
  `reason`. EXCHANGE has two changes; PERMANENT_CHANGE has one MOVE per remaining weekly lecture.
- **Status**: DRAFT, IN_REVIEW, APPLIED, REJECTED, WITHDRAWN, EXPIRED, REVERTED. `conflicted` + `conflicts[]` (types:
  FACULTY_BUSY, DIVISION_BUSY, BATCH_BUSY, ROOM_OCCUPIED, SAME_SLOT_TICKET, OUTSIDE_CALENDAR, OUTSIDE_SLOT_GRID,
  OCCURRENCE_CHANGED) are recomputed on create/edit/review.
- **Approval**: a Faculty applies their own reschedule/cancel/substitute/extra/exchange directly (`approvalPath SELF`).
  If reviewers were asked, the last answer applies it (`REVIEWED`). A permanent change needs an HOD approval; the author
  never applies it alone (409 `rescheduling.needs_hod_approval`). A rejected ticket can be edited (back to DRAFT) or
  escalated to an HOD.
- **Slot grid**: Mon to Sat (`dayOfWeek` 1-6), hour-aligned starts 08:00 to 20:00. Lecture keeps its length. In-person
  needs `roomId`; online must not have one and may have `meetingLink`.
- **Optimistic locking**: `PUT` needs the current `version`; on `platform.concurrent_modification` reload.
- **Visibility**: someone who may not see a ticket/lecture gets 404, not 403.
- **Live view fields**: `cancelled`, `changed`, `extra`, `originalDate/StartTime/EndTime`, `originalFacultyId` (substitute).
  `facultyId` is the current teacher.
- **Expiry**: a ticket expires when the lecture starts (409 `rescheduling.ticket_expired`).

## 4. Problem codes to add to `MESSAGES` (`src/lib/api/problem.ts`)

```
rescheduling.blocked_by_conflicts   rescheduling.ticket_expired       rescheduling.ticket_not_found
rescheduling.ticket_not_draft       rescheduling.ticket_not_editable   rescheduling.ticket_not_in_review
rescheduling.ticket_not_rejected    rescheduling.ticket_not_applied    rescheduling.review_already_decided
rescheduling.reviews_pending        rescheduling.needs_hod_approval    rescheduling.stale_ticket
rescheduling.lecture_not_found      rescheduling.lecture_started       rescheduling.lecture_cancelled
rescheduling.lecture_changed_since  rescheduling.original_slot_passed   rescheduling.not_revertible
rescheduling.revert_exists          rescheduling.not_weekly            rescheduling.assignment_not_found
timetable.lecture_not_found         timetable.version_not_found        timetable.version_published
timetable.version_empty             timetable.clash                    timetable.slot_taken
timetable.effective_from_taken      timetable.entry_not_found          timetable.changes_would_be_lost
timetable.reference_missing
issues.issue_not_found              issues.issue_not_open              issues.place_not_found
issues.ticket_not_found             notifications.notification_not_found
reference.duplicate_code            reference.duplicate_teaching_assignment
reference.department_not_found      reference.division_not_found       reference.batch_not_found
reference.faculty_not_found         reference.room_not_found           reference.subject_not_found
reference.term_not_found            reference.teaching_assignment_not_found
```
Field errors (`errors[]` on `platform.validation_failed`) use `field` + `code` + `message`; see
`TicketSupport.validatedReviewers` for reviewer codes (`duplicate_reviewer`, `hod_required`).

## 5. Blockers (fixed in the plan)

| # | Finding | Evidence | Fixed in |
|---|---|---|---|
| B1 | Every reference read needs `timetable.config.manage` (Super Admin). A teacher cannot list rooms (reschedule target), faculty (substitute), their own teaching assignments (extra lecture); a CR cannot find faculty to mention. | `reference/application/CampusService.java`, `CatalogService.java`, `TeachingAssignmentService.java` | Phase 1: `/api/v1/directory/*` |
| B2 | No "who am I on campus": `/me` `roles` omits scoped roles; nothing returns the caller's faculty id, places or assignments. | `identity/web/MeResponse.java`, `RoleAssignmentService.rolesOf` | Phase 1: `/api/v1/directory/me`, `/api/v1/me/scoped-roles` |
| B3 | `submit` and `escalate` need reviewer user ids; no endpoint lets a teacher find the HOD/CR/BR. `/users` is admin-only. | `rescheduling/web/SubmitTicketRequest.java`, `EscalateRequest.java` | Phase 2: `reviewer-candidates` |
| B4 | Ticket, issue, notification responses carry bare UUIDs (author, faculty, reviewer, room, lecture, division). Only the live timetable has names. | `TicketResponse`, `ChangeResponse`, `TargetResponse`, `IssueResponse`, `NotificationResponse` | Phase 2 (tickets), Phase 3 (issues, notifications) |
| B5 | A student is notified of an applied ticket but gets 404 opening it, and the notification has no lecture reference. | `notifications/application/Audience.java`, `Notification.java` | Phase 3: `lectureId` on the notification |
| B6 | A reviewer cannot list "tickets waiting for me" (the list mixes everything they may see), and nobody can ask which rooms are free for a slot (the author must guess a room and read the conflict). | `rescheduling/application/TicketService.list`, `timetable/application/ClaimStore` | Phase 2: `awaitingReview` filter, `GET /timetable/free-rooms` |

Detailed specs of every fix: [phase-1.md](phase-1.md), [phase-2.md](phase-2.md), [phase-3.md](phase-3.md). The API shapes
are in [api-reference.md](api-reference.md).

Design note: a flat permission held only through a *scoped* role is not honoured (`access/persistence/StoredPermissionSource.java`
reads roles without a scope type only), and a permission added to a role template does not reach roles that already exist
(`RoleTemplateSeeder` leaves existing roles alone). So the new lookup endpoints are `@NoAuthorization` + verified email
(campus names are not confidential), not a new permission. Pattern: `reference/application/AcademicDirectoryService.java`.

## 6. Smaller gaps

- Faculty `userId` is set only at creation, no relink (fixed in Phase 3).
- No timetable entry edit (delete and recreate). Accepted.
- Only two Keycloak demo accounts exist (`demo`, `demo-admin`; `infra/keycloak/overlays/local/main.tf`). Phase 3 adds one per role.
- 15 existing frontend unit tests fail with or without Timetia changes (file upload: `tests/unit/avatar.test.tsx`,
  `import-screen.test.tsx`, `note-attachment.test.tsx`). Not a regression; Phase 3 fixes or quarantines them.
- `access.ScopeHolders.usersIn` returns everyone with any role in a place (a batch's BR and its students); reviewer
  candidates need a permission filter (Phase 2).

## 7. Added to scope

- **Free-room suggestions** (Phase 2): `GET /api/v1/timetable/free-rooms?date&startTime&endTime&sessionType` returns rooms
  without a ROOM claim in that hour, each with `fits`. The spec says the system only *suggests* free rooms that fit (room
  type, seats, projector); seats and projector are not modelled, so the proposed rule is room type vs session type only
  (LAB fits LAB, THEORY fits CLASSROOM, TUTORIAL fits TUTORIAL_ROOM or CLASSROOM). Spec:
  `timetia-docs/modules/lecture-rescheduling/rules/rooms-and-location.md`.

## 8. Deferred (not built; recorded as out of scope for the course project)

| Feature | Spec | Note |
|---|---|---|
| Ticket history timeline | `requirements/audit-log.md` | Events are recorded; the admin Audit page lists them. Permissions `rescheduling.ticket-audit.view*` exist but no endpoint. |
| Holidays and exam weeks, configurable slot grid and calendar | `rules/conflict-rules.md` | Grid hardcoded 08-20; no holiday table. |
| Shared courses | `rules/shared-courses.md` | Decided in the spec, not implemented. |
| Room fit as a *conflict* (ROOM_UNFIT) | `rules/rooms-and-location.md` | The Phase 2 suggestion only advises. |
| Plain-information notes (projector, load, short notice) | `rules/faculty-load.md` | |
| `occurrence.*` events for attendance | `overview/scope.md` | |
| Re-check of tickets when a new timetable version is published | `integration/timetable-version-changes.md` | Publish is refused instead. |
| Super-HOD approval of an HOD's own lecture | `rules/approval-model.md` | |
| Notification channels other than in-app, reminders | `notifications/triggers.md` | Decided: in-app only in version 1. |

The `timetia-docs` contradiction on shared courses (rules say decided, `research/data-model-fixes.md` F-J7 says deferred)
should be settled by marking it deferred in Phase 3.
