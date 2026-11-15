# Domain: what the UI must know

The rules below are read from the backend code, not from the product docs. Source files are named so each rule can be
checked: paths are relative to `backend/src/main/java/com/kibootia/backend/`.

## 1. Vocabulary

| Word | Meaning | In the API |
|---|---|---|
| Department | e.g. CSE. Holds divisions and faculty. | `departmentId` |
| Division | A class, e.g. CSE-A. Attends **theory** lectures together. | `divisionId`, `divisionCode` |
| Batch | Part of a division, e.g. CSE-A1. Attends **lab** and **tutorial** sessions. | `batchId`, `batchCode` |
| Session type | `THEORY`, `LAB`, `TUTORIAL`. Theory is for the whole division (`batchId` null); lab/tutorial is for one batch. | `sessionType` |
| Faculty | A teacher record. Linked to a sign-in account by `userId` (may be null). A faculty **id is not a user id**. | `facultyId` vs `...UserId` |
| Teaching assignment | "Faculty F teaches subject S to division D (batch B) as session type T". | `assignmentId` |
| Timetable version | A division's weekly timetable from a date. `DRAFT` or `PUBLISHED`. | `/timetable/versions` |
| Entry | One weekly slot of a version: day 1 to 6 (Mon to Sat), whole hours. | `/versions/{id}/entries` |
| Lecture (occurrence) | One entry on one date. The unit tickets change. | `lectureId`, `/timetable/live` |
| Slot | date + start + end + mode (+ room or meeting link). | `target`, `before` |
| Mode | `IN_PERSON` (needs `roomId`, no link) or `ONLINE` (no room, optional `meetingLink`). | `mode` |
| Ticket | A proposed change to one or more lectures. | `/tickets` |
| Change | What a ticket does to one lecture: `MOVE`, `CANCEL`, `REASSIGN_FACULTY`, `CREATE_EXTRA`, `RESTORE`. | `change`, `changes[]` |
| Review | One person asked to answer a ticket, in a capacity. | `reviews[]` |
| Issue | A request a CR/BR opens; changes nothing by itself. | `/issues` |

**Slot grid** (`timetable/application/LectureCalendarService.java`, `calendarProblems`): Monday to Saturday, never
Sunday; start and end on the hour; start ≥ 08:00, end ≤ 20:00; inside the term's dates. The campus time zone is the
backend's `kibootia.timetable.time-zone` (default `Asia/Kolkata`); "has the lecture started" is judged in that zone.

## 2. Roles and what each may do

Seeded at start-up by `academic/application/RoleTemplateSeeder.java` from `academic/domain/AcademicRoles.java`.

| Role | Given in | Permissions it carries |
|---|---|---|
| Super Admin (built-in Administrator) | everywhere | `timetable.live.view-all`, `timetable.config.manage`, plus all platform admin permissions. **No ticket or issue permission.** |
| Super HOD | everywhere | same as HOD, for every department |
| HOD | one department | `timetable.live.view`, `rescheduling.tickets.create-on-behalf`, `.cancel-on-behalf`, `.review`, `.reject`, `.approve-permanent`, `.revert-in-scope`, `.edit-own`, `.view-in-scope`, `rescheduling.ticket-audit.view-in-scope`, `rescheduling.issues.view-in-scope` |
| Faculty | everywhere (global role; what it reaches is decided per lecture) | `timetable.live.view-own`, `.view-division`, `rescheduling.tickets.create`, `.cancel`, `.self-approve`, `.review`, `.reject`, `.revert`, `.edit-own`, `.view`, `rescheduling.ticket-audit.view`, `rescheduling.issues.close`, `.view` |
| CR | one division | `timetable.live.view`, `rescheduling.tickets.review`, `.reject`, `.view-in-scope`, `rescheduling.ticket-audit.view-in-scope`, `rescheduling.issues.open`, `.close`, `.view`, `.view-in-scope` |
| BR | one batch | same as CR |
| Student | one batch | `timetable.live.view` |

Consequences the UI must respect:
- An HOD who also teaches needs the **Faculty** role too; `create`/`self-approve` are Faculty's.
- `GET /api/v1/me` → `permissions: string[]` contains the names above for every role the person holds (flat and in a
  place), without saying *where*. Use it only to decide which navigation and buttons to offer.
- Holding a permission is necessary, not sufficient: the policy (`academic/domain/AcademicAccessPolicy.java`) then checks
  the record. The **session rule**: a division grant (CR) counts for theory lectures only, a batch grant (BR) for
  lab/tutorial only, for review, ticket visibility and issues. HOD and Super HOD have no such limit.
- Status codes: **404** = you may not see it (or it does not exist), **403** = you see it but may not do that,
  **409** = the state or a conflict forbids it, **400** = a field is wrong (`errors[]`).

### Who sees which lecture (`timetable/application/LiveTimetableService.java`)

| Person | Sees |
|---|---|
| Super Admin | every lecture |
| Super HOD | every lecture |
| HOD of a department | every lecture of that department |
| Faculty | lectures they teach (including as a substitute), and every lecture of any division they have a teaching assignment in |
| CR of a division | every lecture of that division |
| BR / Student of a batch | the batch's lab/tutorial lectures and the theory lectures of the batch's division |
| anyone else | nothing (empty list) |

The list is filtered by the server; the UI never filters for permission, only for convenience (division, batch, faculty).

## 3. Tickets

### 3.1 Types and what the create request needs (`rescheduling/application/TicketService.java`)

`POST /api/v1/tickets`. A field not listed for a type must be **absent**, or the request fails with 400 and the field
error code shown.

| `type` | Required | Forbidden (`not_allowed`) | Changes made | Extra validation (field → code) |
|---|---|---|---|---|
| `RESCHEDULE` | `lectureId`, `target` | `otherLectureId`, `assignmentId`, `substituteFacultyId` | one `MOVE` | see target rules below |
| `CANCEL` | `lectureId`, `reason` (not blank) | `target`, `otherLectureId`, `assignmentId`, `substituteFacultyId` | one `CANCEL` | `reason` → `required` |
| `SUBSTITUTE` | `lectureId`, `substituteFacultyId` | `target`, `otherLectureId`, `assignmentId` | one `REASSIGN_FACULTY` | `substituteFacultyId` → `required`, `same_faculty` (already teaches it), `unknown_faculty` |
| `EXTRA` | `assignmentId`, `target` | `lectureId`, `substituteFacultyId` | one `CREATE_EXTRA` | assignment not the caller's (or their department's for an HOD) → 404 `rescheduling.assignment_not_found` |
| `EXCHANGE` | `lectureId`, `otherLectureId` | `target`, `assignmentId`, `substituteFacultyId` | two `MOVE`s (each takes the other's date, start, room, mode; keeps its own length) | `otherLectureId` → `same_lecture`, `different_session`, `different_batch`, `same_faculty`, `too_long`; other lecture must be in the same division and visible to the author, else 404 `rescheduling.lecture_not_found` |
| `PERMANENT_CHANGE` | `lectureId`, `target` | `otherLectureId`, `assignmentId` | one `MOVE` per remaining lecture of the same weekly entry, shifted by the same number of days; cancelled, already-changed and started ones are skipped | not a weekly lecture (an extra) → 409 `rescheduling.not_weekly` |
| `REVERT` | never sent to `POST /tickets`; created by `POST /tickets/{id}/revert` | | one `RESTORE` per change of the original | |

Optional on all: `description` (≤ 1000 chars). `reason` ≤ 500 chars.

**Target rules** (`validateTarget`): `target.roomId` → `room_required` (in person without room), `unknown_room`,
`room_not_allowed` (online with room); `target.meetingLink` → `link_not_allowed` (in person with link); `target.endTime` →
`same_length` (a move keeps the lecture's length in hours; an extra lecture chooses its own); `target.date` →
`in_the_past`; `target` → `no_change` (identical to where it is), `required`.

**Who may raise**: the teacher of the lecture (`tickets.create`, or `tickets.cancel` for a cancel), or an HOD of its
department on their behalf (`create-on-behalf` / `cancel-on-behalf`). When an HOD acts, the response has
`actingFacultyId` = the teacher. A lecture that is cancelled → 409 `rescheduling.lecture_cancelled`; one that has started
→ 409 `rescheduling.lecture_started`. A lecture the caller cannot see → 404 `rescheduling.lecture_not_found`; one they see
but may not change (a colleague's, in a division they teach in) → 403.

**A ticket with conflicts is still created** (201) with `conflicted: true` and `conflicts[]`. It cannot be applied until
the conflict is gone; it can be edited, sent for review, or withdrawn.

### 3.2 Lifecycle (`rescheduling/domain/TicketStatus.java`)

```
                 submit                      last reviewer answers, none rejected
   ┌────────┐ ───────────► ┌───────────┐ ───────────────────────────────► ┌─────────┐   revert ticket applied   ┌──────────┐
   │ DRAFT  │              │ IN_REVIEW │                                  │ APPLIED │ ────────────────────────► │ REVERTED │
   └────────┘ ◄─────────── └───────────┘                                  └─────────┘                           └──────────┘
     │  │  ▲   edit a          │    │ a reviewer rejects                       ▲
     │  │  │   rejected one    │    ▼                                          │
     │  │  │   (revision+1) ┌──────────┐  escalate (revision+1, HOD decides)   │
     │  │  └─────────────── │ REJECTED │ ───────────► IN_REVIEW                │
     │  │                   └──────────┘                                       │
     │  └── apply (author, no review) ─────────────────────────────────────────┘
     ├── withdraw ──► WITHDRAWN        (also from IN_REVIEW)
     └── lecture starts first ──► EXPIRED   (also from IN_REVIEW; checked lazily and by a scheduled job)
```

`WITHDRAWN`, `EXPIRED`, `REVERTED` are final. `expiresAt` = start of the earliest lecture or target slot involved.
`revisionNo` starts at 1 and rises when a rejected ticket is edited or escalated; only reviews of the current revision
count. `version` is the optimistic-lock number an edit must send back.

### 3.3 Actions: endpoint, who, when

"Author" = `ticket.authorUserId === me.id`. All actions first require that the caller can see the ticket (else 404).

| Action | Endpoint | Server allows when | Result |
|---|---|---|---|
| Edit | `PUT /tickets/{id}` body: `description`, `target`, `reason`, `substituteFacultyId`, `reviewers?`, `version` | author; type not `REVERT`/`PERMANENT_CHANGE`; status `DRAFT`, `IN_REVIEW` or `REJECTED`; `version` current (else 409 `rescheduling.stale_ticket`). The whole plan is replaced: send the fields the type needs again. For `EXCHANGE` send only `description` + `version`. `reviewers` only while `IN_REVIEW`. | `REJECTED` → `DRAFT`, revision+1. `IN_REVIEW` with a changed plan: approvals cleared, reviewers told again. Conflicts re-checked. |
| Apply | `POST /tickets/{id}/apply` | author, and: **DRAFT** → not `PERMANENT_CHANGE` (409 `rescheduling.needs_hod_approval`); `CANCEL`: may cancel the lecture (teacher or HOD); `REVERT`: author/affected faculty or HOD in scope; every other type needs `self-approve`, which only the **teacher of the lecture** holds, so an HOD acting on behalf gets 403 and must send it for review. **IN_REVIEW** → everyone asked has answered and none rejected (else 409 `rescheduling.reviews_pending`); a permanent change also needs an HOD-capacity approval. Other statuses → 409 `rescheduling.ticket_not_draft`. | `APPLIED` (`approvalPath` `SELF` or `REVIEWED`), or **409 `rescheduling.blocked_by_conflicts`** with `errors[]` (one per conflict, `field: "conflicts"`, `code` = conflict type in lower case, `message`), or 409 `rescheduling.ticket_expired`. |
| Send for review | `POST /tickets/{id}/submit` body `{reviewers:[{userId, capacity}]}` | author; status `DRAFT`. Reviewers: at least one (`reviewers` → `required`), not the author (`reviewer_is_author`), must be able to sign in (`unknown_reviewer`), no duplicates (`duplicate_reviewer`); a permanent change needs one with capacity `HOD` (`hod_required`). | `IN_REVIEW` |
| Review | `POST /tickets/{id}/review` body `{decision, reason?}` | caller has a `PENDING` review in the current revision and holds `review` (`reject` to reject) for the lecture; status `IN_REVIEW` (else 409 `rescheduling.ticket_not_in_review`); already answered → 409 `rescheduling.review_already_decided`. `REJECT` needs `reason` (`reason` → `required`). An HOD reviewer of a permanent change may not `ACKNOWLEDGE` (`decision` → `approval_required`). | One rejection → `REJECTED` at once. Last non-rejecting answer → applied automatically, unless a conflict stands: then it stays `IN_REVIEW`, `conflicted: true`, and the author applies later. |
| Escalate | `POST /tickets/{id}/escalate` body `{hodUserId}` | author; status `REJECTED` (else 409 `rescheduling.ticket_not_rejected`) | `IN_REVIEW`, revision+1, one review (capacity `HOD`, `escalation: true`) |
| Withdraw | `POST /tickets/{id}/withdraw` | author; status `DRAFT` or `IN_REVIEW` | `WITHDRAWN` |
| Revert | `POST /tickets/{id}/revert` body `{reason?}` | status `APPLIED` (else 409 `rescheduling.ticket_not_applied`); type `RESCHEDULE`, `CANCEL`, `SUBSTITUTE`, `EXCHANGE` or `PERMANENT_CHANGE` (else 409 `rescheduling.not_revertible`, an extra lecture is cancelled instead); caller is the author or an affected faculty member (`revert`, not for a permanent change) or an HOD in scope (`revert-in-scope`); no open revert exists (409 `rescheduling.revert_exists`); no later ticket changed the lecture (409 `rescheduling.lecture_changed_since`); lecture not started; original slot not passed (409 `rescheduling.original_slot_passed`). | **201 with a new `REVERT` ticket in `DRAFT`** (`revertsTicketId` set). Apply it like any ticket; then the original becomes `REVERTED` with `revertedByTicketId`. |

Reviewer capacities (`ReviewerCapacity`): `HOD`, `CR`, `BR`, `AFFECTED_FACULTY`, `OTHER_FACULTY`. The capacity is a label;
what the person may do comes from their role. Asking someone who cannot review leaves the ticket waiting.

### 3.4 Which buttons to show: the spec of `availableActions(ticket, me)`

The frontend derives the buttons from the **detail** response (`GET /tickets/{id}`; the list has no `reviews`). This is a
hint: when the server disagrees it answers 403/409 and the UI shows the message.

```
author        = ticket.authorUserId === me.id
current       = ticket.reviews.filter(r => r.revisionNo === ticket.revisionNo)
myPending     = current.find(r => r.reviewerUserId === me.id && r.status === "PENDING")
verdict       = current.some(r => r.status === "REJECTED") ? "REJECTED"
              : current.length === 0 || current.some(r => r.status === "PENDING") ? "WAITING" : "APPROVED"
hodApproved   = current.some(r => r.capacity === "HOD" && r.status === "APPROVED")
onBehalf      = ticket.actingFacultyId != null          // an HOD raised it for a teacher

edit      : author && !["REVERT","PERMANENT_CHANGE"].includes(type) && ["DRAFT","IN_REVIEW","REJECTED"].includes(status)
apply     : author && (
              (status === "DRAFT" && type !== "PERMANENT_CHANGE" && (type === "CANCEL" || type === "REVERT" || !onBehalf))
           || (status === "IN_REVIEW" && verdict === "APPROVED" && (type !== "PERMANENT_CHANGE" || hodApproved)) )
submit    : author && status === "DRAFT"
withdraw  : author && (status === "DRAFT" || status === "IN_REVIEW")
review    : status === "IN_REVIEW" && myPending != null
            → decisions: ["APPROVE","REJECT"] + ("ACKNOWLEDGE" unless type === "PERMANENT_CHANGE" && myPending.capacity === "HOD")
escalate  : author && status === "REJECTED"
revert    : status === "APPLIED" && revertedByTicketId == null
            && ["RESCHEDULE","CANCEL","SUBSTITUTE","EXCHANGE","PERMANENT_CHANGE"].includes(type)
            && ( me.permissions.includes("rescheduling.tickets.revert-in-scope")
              || (type !== "PERMANENT_CHANGE" && me.permissions.includes("rescheduling.tickets.revert")) )
```

Primary action per state (shown as the filled button; the rest are outline): `DRAFT` → Apply if available, else Send for
review; `IN_REVIEW` → Review if `myPending`, else Apply if available; `REJECTED` → Edit; `APPLIED` → none (Revert is an
outline button).

### 3.5 Conflicts (`timetable/ConflictType.java`)

| Type | Label to show | Meaning |
|---|---|---|
| `FACULTY_BUSY` | Teacher is busy | The teacher (or substitute) has another lecture then |
| `DIVISION_BUSY` | Division is busy | The division has a theory lecture then |
| `BATCH_BUSY` | Batch is busy | The batch has a lecture then |
| `ROOM_OCCUPIED` | Room is taken | The room holds another lecture then |
| `SAME_SLOT_TICKET` | Another ticket took this slot | An applied ticket moved a lecture there first |
| `OUTSIDE_CALENDAR` | Outside the calendar | A Sunday, or outside the term |
| `OUTSIDE_SLOT_GRID` | Outside teaching hours | Not on the hour between 08:00 and 20:00 |
| `OCCURRENCE_CHANGED` | The lecture changed since | The lecture was changed after the ticket was planned; edit the ticket to re-plan |

Each conflict has `message` (a full sentence from the server; show it as is), `lectureId`, `conflictingLectureId`,
`conflictingTicketId` (link to it when present). Nobody can override a conflict.

## 4. Issues (`issues/application/IssueService.java`)

| Action | Endpoint | Who | Rules |
|---|---|---|---|
| Open | `POST /issues` `{sessionType, divisionId?, batchId?, title, description?, mentionedFacultyIds?}` | CR of the division for `THEORY`; BR of the batch for `LAB`/`TUTORIAL` | `THEORY` needs `divisionId` and no `batchId`; lab/tutorial needs `batchId` and no `divisionId` (`required` / `not_allowed`). `title` ≤ 200, `description` ≤ 2000, ≤ 10 mentions (`mentionedFacultyIds` → `unknown_faculty`). Unknown place → 404 `issues.place_not_found`. Not their place → 403. |
| See | `GET /issues`, `GET /issues/{id}` | opener; mentioned faculty; CR (theory issues of the division); BR (lab/tutorial issues of the batch); HOD (the department's) | filters `status=OPEN\|CLOSED`, `mine=true` |
| Mention more | `POST /issues/{id}/mentions` `{facultyId}` | the opener only (others who see it → 403) | issue must be `OPEN` (409 `issues.issue_not_open`); already mentioned = no-op |
| Link a ticket | `POST /issues/{id}/links` `{ticketId}` | opener or a mentioned faculty member | `OPEN`; the ticket must be visible to the caller, else 404 `issues.ticket_not_found` |
| Close | `POST /issues/{id}/close` | opener or a mentioned faculty member | `OPEN` → `CLOSED` (`closedAt`, `closedByUserId`). There is no reopen. |

An HOD sees issues read-only. An issue never changes the timetable; the mentioned teacher raises a ticket and links it.

## 5. Notifications (`notifications/application/Audience.java`)

In-app only. Created asynchronously a moment after the action commits. Each person reads only their own.

| `type` | Who receives it | Sentence to show (`{actor}` = `actorName`, `{kind}` = ticket type label in lower case, "ticket" when unknown) | Link |
|---|---|---|---|
| `REVIEW_REQUESTED` | the reviewers asked (also the HOD on escalation) | {actor} asked you to review a {kind} | ticket |
| `REVIEW_APPROVED` | the author | {actor} approved your {kind} | ticket |
| `REVIEW_REJECTED` | the author | {actor} rejected your {kind} | ticket |
| `TICKET_APPLIED` | author, faculty involved, HOD(s) of the department, CR(s) of the division (theory only), BR(s) and students of the batches | A {kind} was applied | ticket if the person may see tickets, else the lecture |
| `TICKET_REVERTED` | same as applied | A {kind} was undone | same |
| `TICKET_CONFLICTED` | author, HOD(s) of the department | Something is now in the way of a {kind} | ticket |
| `TICKET_EDITED` | the reviewers | {actor} changed a {kind} you were asked to review. Please look again | ticket |
| `TICKET_WITHDRAWN` | the reviewers | {actor} withdrew a {kind} you were asked to review | ticket |
| `ISSUE_MENTION` | each mentioned faculty member with an account | {actor} mentioned you on an issue | issue |

"May see tickets" = holds `rescheduling.tickets.view` or `rescheduling.tickets.view-in-scope`. Students do not, so their
link goes to the lecture (`/timetable?lecture={lectureId}`); `lectureId`, `ticketType` and `actorName` arrive in Phase 3
(until then show the generic sentence and link the ticket only for people who may see tickets).

## 6. Labels

```ts
export const TICKET_TYPE_LABEL = {
  RESCHEDULE: "Reschedule", CANCEL: "Cancellation", SUBSTITUTE: "Substitute", EXTRA: "Extra lecture",
  EXCHANGE: "Exchange", REVERT: "Revert", PERMANENT_CHANGE: "Permanent change",
} as const;
export const TICKET_STATUS_LABEL = {
  DRAFT: "Draft", IN_REVIEW: "In review", APPLIED: "Applied", REJECTED: "Rejected",
  WITHDRAWN: "Withdrawn", EXPIRED: "Expired", REVERTED: "Reverted",
} as const;
// Badge variant per status (components/ui/badge.tsx variants: default, secondary, destructive, outline, ghost)
export const TICKET_STATUS_VARIANT = {
  DRAFT: "outline", IN_REVIEW: "secondary", APPLIED: "default", REJECTED: "destructive",
  WITHDRAWN: "ghost", EXPIRED: "ghost", REVERTED: "ghost",
} as const;
export const REVIEW_STATUS_LABEL = { PENDING: "Waiting", APPROVED: "Approved", REJECTED: "Rejected", ACKNOWLEDGED: "Acknowledged" } as const;
export const CAPACITY_LABEL = { HOD: "HOD", CR: "Class representative", BR: "Batch representative",
  AFFECTED_FACULTY: "Affected teacher", OTHER_FACULTY: "Other teacher" } as const;
export const SESSION_LABEL = { THEORY: "Theory", LAB: "Lab", TUTORIAL: "Tutorial" } as const;
export const MODE_LABEL = { IN_PERSON: "In person", ONLINE: "Online" } as const;
export const ROOM_TYPE_LABEL = { CLASSROOM: "Classroom", TUTORIAL_ROOM: "Tutorial room", LAB: "Lab" } as const;
export const DAY_LABEL = ["", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday"] as const; // index = dayOfWeek
```

## 7. Problem codes and the words to show

Add these lines to `MESSAGES` in `frontend/src/lib/api/problem.ts` (the map is keyed by `problem.code`; `messageFor`
falls back to "The request failed."). Every code below exists in the backend today except those marked NEW (added in the
phase named).

```ts
// --- Timetia: timetable ---------------------------------------------------------------------------------------
"timetable.lecture_not_found": "That lecture could not be found, or you may not see it.",
"timetable.version_not_found": "That timetable version could not be found.",
"timetable.entry_not_found": "That entry is not in this version.",
"timetable.version_published": "A published timetable cannot be changed. Create a new version instead.",
"timetable.version_empty": "Add at least one lecture before publishing.",
"timetable.effective_from_taken": "This division already has a published timetable starting on that date.",
"timetable.reference_missing": "Something this timetable refers to (a term, an assignment or a room) no longer exists.",
"timetable.clash": "This would book a teacher, a room or a group of students twice.",
"timetable.slot_taken": "Someone else took that slot a moment ago. Check the timetable and try again.",
"timetable.changes_would_be_lost": "Lectures this version would replace have applied changes. Revert those tickets first.",
// --- Timetia: tickets -----------------------------------------------------------------------------------------
"rescheduling.ticket_not_found": "That ticket could not be found, or you may not see it.",
"rescheduling.lecture_not_found": "That lecture could not be found, or you may not see it.",
"rescheduling.assignment_not_found": "That teaching assignment could not be found, or it is not yours.",
"rescheduling.lecture_started": "The lecture has started, so it can no longer be changed.",
"rescheduling.lecture_cancelled": "The lecture is cancelled.",
"rescheduling.blocked_by_conflicts": "Something is in the way. Nothing was changed.",
"rescheduling.ticket_expired": "The lecture started before the ticket was applied, so the ticket has expired.",
"rescheduling.ticket_not_draft": "This can only be done to a draft.",
"rescheduling.ticket_not_editable": "This ticket can no longer be edited.",
"rescheduling.ticket_not_in_review": "This ticket is not under review.",
"rescheduling.ticket_not_rejected": "Only a rejected ticket can be taken to the HOD.",
"rescheduling.ticket_not_applied": "Only an applied ticket can be reverted.",
"rescheduling.review_already_decided": "You have already answered this review.",
"rescheduling.reviews_pending": "Not everyone asked to review has answered yet.",
"rescheduling.needs_hod_approval": "A permanent change needs an HOD's approval. Send it for review to an HOD.",
"rescheduling.stale_ticket": "The ticket was changed after you opened it. Reload it and try again.",
"rescheduling.not_weekly": "Only a lecture of the weekly timetable can be changed permanently.",
"rescheduling.not_revertible": "This kind of ticket cannot be reverted. To undo an extra lecture, cancel it.",
"rescheduling.revert_exists": "A revert of this ticket has already been raised.",
"rescheduling.lecture_changed_since": "Another ticket has changed the lecture since, so this one cannot be reverted on its own.",
"rescheduling.original_slot_passed": "The slot the lecture came from has passed, so it cannot go back.",
// --- Timetia: issues and notifications ------------------------------------------------------------------------
"issues.issue_not_found": "That issue could not be found, or you may not see it.",
"issues.issue_not_open": "The issue is closed.",
"issues.place_not_found": "That division or batch could not be found.",
"issues.ticket_not_found": "That ticket could not be found, or you may not see it.",
"notifications.notification_not_found": "That notification could not be found.",
// --- Timetia: campus data -------------------------------------------------------------------------------------
"reference.duplicate_code": "Something with this code already exists.",
"reference.duplicate_teaching_assignment": "That subject is already assigned for this division, session type and batch.",
"reference.department_not_found": "That department could not be found.",
"reference.division_not_found": "That division could not be found.",
"reference.batch_not_found": "That batch could not be found.",
"reference.subject_not_found": "That subject could not be found.",
"reference.room_not_found": "That room could not be found.",
"reference.faculty_not_found": "That faculty member could not be found.",
"reference.term_not_found": "That term could not be found.",
"reference.teaching_assignment_not_found": "That teaching assignment could not be found.",
```

For a **validation failure** (`platform.validation_failed`, 400) the body has `errors: [{field, code, message}]`. The
`message` is written for developers but is plain English in this backend; `applyProblem` (`src/lib/forms.ts`) puts it on
the form field named `field`. Nested fields arrive as `target.roomId`, `target.date`, `target.endTime`,
`target.meetingLink`: map them onto the form's own field names (architecture.md, "Forms").

Campus data is **soft-deleted** (`deleted_at`), so deleting a room, subject or faculty member that lectures still refer
to succeeds. The lectures stay, and their `roomCode`, `subjectCode`, `subjectName`, `facultyName`, `divisionCode`,
`batchCode` become `null`. Every screen must therefore render a null name as a placeholder ("deleted room", "deleted
teacher") and never crash on it. The setup screens (Phase 3) ask for confirmation before a delete and say this.
