# Data model audit against the docs branch

- **Type:** research-note
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-09

Audited: the scraped "Complete Database Modelling, Standalone Prototype" (40 tables) against branch `docs` of Kizunia/timetia at commit c7444be. Read in full: the base spec (now archived), everything under `modules/lecture-rescheduling/` (rules, notifications, integration, open questions, changelog, the 2026-10-30 ticket lifecycle) and `shared/glossary/`. Rule used: module files win over the base spec, as the docs themselves say. The fixed design is in [data-model-fixes.md](data-model-fixes.md).

> **Update 2026-11-09.** The audit was done against docs commit c7444be. Since then the docs added [ADR 0001 (standalone in version 1)](../modules/lecture-rescheduling/decisions/0001-standalone-version-1.md), archived the original spec, moved shared courses to a per-batch seeded list and widened the revert right. Finding A1 is therefore settled in favour of the model, and the "skipped shared courses" finding J7 is superseded. Spec section numbers below refer to the archived original spec. The owner also chose edit-and-resubmit for rejections ([ADR 0006](../modules/lecture-rescheduling/decisions/0006-rejected-tickets-edit-and-resubmit.md)).

## 1. Verdict

The model's backbone is right: base timetable kept separate from the effective schedule, dated occurrences, ticket with items, before/after history, first approved wins, no overrides, issues that never change the timetable, append-only audit. About 20 decisions line up with the docs (section 4).

It does not yet match the docs in four ways that matter:

1. **It changes the product.** The docs describe a module that plugs into the ERP and never owns users or the base timetable. The model makes the module own both. This needs your decision before anything else (A1).
2. **The ticket workflow is the old one.** The docs dropped Open and Approved and added Reverted-as-a-new-ticket on 2026-10-30. The model still has the old states and a revert inside the same ticket (B1, B2).
3. **"Every conflict blocks" is not enforceable.** Nothing in the schema stops two tickets double-booking a room or faculty, and findings cannot point at calendars, rooms-unfit, other tickets or permanent changes (C1 to C4).
4. **Data the rules depend on is missing:** slot grid, W.E.F. date of a timetable version, room floor/number/lab kind, a subject's required room type and projector need, issue scope, who a HOD acts for, approval path (D1, D2, E1, F1, H1, B3).

Counts: 3 critical, 20 major, 14 minor, 1 deferred (38 findings).

## 2. Decisions (all answered)

| # | Decision | Outcome |
| --- | --- | --- |
| 1 | Standalone prototype or ERP-coupled (A1) | **Standalone** (confirmed 2026-11-09; already recorded in ADR 0001). |
| 2 | After a rejection (B3) | **Edit and resubmit** on the same ticket (2026-11-09). |
| 3 | Who may revert (B2) | **Author, affected faculty and HOD**, before the lecture starts (2026-11-08). |
| 4 | Open/Approved states gone (B1) | Yes, per the lifecycle doc. |

## 3. Mismatches

Severity: **C** critical, **M** major, **m** minor. "Docs" cites the file that decides it. Fix IDs refer to [data-model-fixes.md](data-model-fixes.md).

### A. Foundations

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| A1 | C | Module owns Department, Program, Student, Faculty, Subject, Division, Batch, assignments, calendar and the base timetable. Sync, `erp_*` refs and SSO are removed. | Spec §4 and §5: creating the base timetable and managing users and roles are **out of scope**; ERP owns them, module is read only; own database; reads by API or scheduled import; never writes ERP. Open question 1 is still "does EduPlusCampus have an API". Nothing in the docs records a standalone decision. | F-A1: settled, ADR 0001. |
| A2 | M | `UserAccount.password_hash`, `last_login_at`: the module authenticates users. | Spec §5: login is single sign-on against the ERP; ERP roles map onto module roles. | F-A2: `auth_provider`, nullable password for the prototype. |
| A3 | M | Outbox examples `LECTURE_MOVED`, `LECTURE_CANCELLED`, `LECTURE_ADDED`. | Spec §5: `occurrence.moved / cancelled / added / swapped / substituted`. Two are missing and names differ. | F-I2 |

### B. Ticket and review workflow

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| B1 | C | Status: OPEN, IN_REVIEW, APPROVED, REJECTED, WITHDRAWN, EXPIRED, APPLIED. No Draft, no Reverted. "Approval and application are separate stages." | `ticket-lifecycle.md` (2026-10-30): Draft, In review, Applied, Rejected, Withdrawn, Expired, Reverted. Open and Approved are dropped because self-approval goes Draft to Applied in one step. | F-B1 |
| B2 | M | `AppliedChange.action_type` APPLY/REVERT under the **same** `ticket_id`. | "Applied is final." A revert is a **new ticket**; the original shows Reverted with a link to the reverting ticket. | F-B2 |
| B3 | M | Nothing records how a ticket was approved, in what capacity a reviewer reviews, who a HOD acts for, or an escalation. | `approval-model.md`: self-approval is a first-class path; reviewers are HOD, CR/BR in scope, affected faculty, other faculty; consent for exchange/substitute is optional; author can escalate a rejection to the HOD; `faculty-on-leave.md`: HOD raises, cancels, substitutes on behalf. | F-B3 |
| B4 | M | No expiry or conflicted marker. | Expires when the lecture starts. A ticket in review with a new conflict stays In review with a "conflicted" flag; author and HOD are notified. | F-B4 |
| B5 | m | `description`, `reason`, `decision_reason` all nullable. | Cancel needs a mandatory reason; a rejection needs a reason. | F-B5 |
| B6 | M | Nothing says who may author a ticket or ties Permanent to a HOD approval. | Tickets are raised by faculty (or HOD for them). CR/BR only open issues. Permanent changes always need the HOD. | F-B6 |
| B7 | M | `TicketChangeItem` has every target field nullable, one line of prose per operation, exchange as "two MOVE items" with no pairing. | Reschedule target is a complete schedule; substitute keeps time and room; exchange moves both or neither; an in-person target needs a room. | F-B7 |
| B8 | m | `updated_at DEFAULT CURRENT_TIMESTAMP` never updates; no submit time for Draft. | Draft is invisible to others, so submission time matters. | F-B8 |

### C. Conflicts

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| C1 | M | `ConflictFinding.severity` BLOCKING/INFORMATIONAL and run result CLEAR/BLOCKED/INFORMATIONAL. | `conflict-rules.md`: **no warning level**. Missing projector, load and short notice are "plain information, not conflicts"; they never block and are not conflicts. | F-C1 |
| C2 | M | `conflict_type`: "FACULTY, ROOM, DIVISION, BATCH, CALENDAR, etc." | Seven checks: faculty, division, batch, room occupied, room unfit, two tickets same slot or room, outside the calendar (holiday, exam week, Sunday, outside 08:00 to 20:00). Room unfit and same-slot ticket have no type and no way to point at the calendar day or the other ticket. | F-C2 |
| C3 | M | Findings reference only `TicketChangeItem`. | Permanent changes also get conflict-checked; they live in `TicketRecurringChangeItem`, so their conflicts cannot be stored. | F-C3 |
| C4 | C | Concurrency left to "appropriate locking". | "First approved wins"; nobody can override. Today only application code stands between two tickets and a double-booked room. | F-C4: `OccurrenceClaim` unique cells. |
| C5 | m | Run triggers SUBMISSION, APPROVAL, PRE_APPLICATION, VERSION_UPDATE. | Checked "when raised and again at approval", where approval and applying are one instant. | F-C5 |

### D. Rooms and location

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| D1 | M | Room: `room_code`, `building_name`, type CLASSROOM/LAB/other, capacity, projector. | Spec §12: building A to E, floor, room number **as separate fields**, code for display, type Classroom / **Tutorial room** / Lab **with lab kind**, seats, projector, **owning department**. Missing: floor, number, tutorial type, lab kind, owning department; building should be A to E, not free text. | F-D1 |
| D2 | M | Subject has only code, name, credits. | Spec §12 data list: type, duration, **required room type**, weekly hours. The rules "room unfit" (a DBMS lab is not the OOP lab) and "needs a projector" cannot be evaluated. | F-D2 |
| D3 | m | Mode `ONLINE/OFFLINE`. Room required for OFFLINE only in `TimetableEntry`. | `rooms-and-location.md`: mode is **in person / online**; every changed in-person lecture has a room even if the original had none; online has none. The ERP "--" means online. Room-for-in-person is not enforced on change items, applied items or effective state. | F-D3 |
| D4 | m | Separate `room_id` and `meeting_link`. | One **location** field holding room number or link. | Keep the model (it is better); amend the docs. |

### E. Calendar and slots

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| E1 | M | No slot grid. Times are free `TIME` values. | Spec §12 and §8.8: slot grid (slot length, breaks, per-department differences) is configured by the Super Admin; hourly cells 08:00 to 20:00; a lab is two slots; "outside 08:00 to 20:00" is a block. | F-E1 |
| E2 | m | `teaching_allowed` beside `day_type`; `day_of_week` 1 to 7. | Calendar is fixed, nobody overrides, Sunday blocks. A second flag invites overrides; day 7 is never teachable. | F-E2 |

### F. Timetable versions

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| F1 | M | Version has `published_at`, no W.E.F. date. `CurrentTimetableVersion` points to one version per division. | Versions carry a **W.E.F. date** (V1, W.E.F. 06-Jul-2026). A pointer cannot say "V1 until 12 Oct, V2 after", which is what keeps past occurrences correct. | F-F1 |
| F2 | m | Published versions "should" be immutable; reconciliation rows have no resolver. Reconciliation ignores permanent overrides. | Kept changes are re-checked and flagged to author and HOD; nothing discarded silently. | F-F2 |

### G. Roles and scope

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| G1 | M | Scope validity "validated by the application". | CR is for **theory** only, BR for **lab and tutorial** only, several CRs per class (no ACR), HOD per department, Super HOD across departments. Nothing in the schema enforces scope-to-role fit. | F-G1 |
| G2 | m | `STUDENT` and `FACULTY` are roles and also profile tables. | Two sources of truth for the same fact. | F-G2 |
| G3 | m | Several active HODs per department possible. | One HOD approves per department. | F-G2 |

### H. Issues

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| H1 | M | `Issue` has no division/batch/type scope and no closer; mentions point at any user. | Only CR and BR open issues, within scope (CR theory, BR lab/tutorial); the **opener or any mentioned faculty** closes it; mentions are faculty. | F-H1 |

### I. Notifications, audit, outbox

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| I1 | M | `notification_type VARCHAR(50)`; links to ticket and occurrence only. | Fixed event list including "an issue mentions you" (needs `issue_id`) and the conflicted notice. In-app only, no reminders: no email columns needed (model agrees). | F-I1 |
| I2 | M | `aggregate_id` with no type; free `event_type`. | Five fixed event names for attendance. | F-I2 |
| I3 | m | `action_type VARCHAR(50)`, no FK to issue, version or role changes. | Every action logged: who, what, when, from which state; the model itself lists "version published". | F-I3 |

### J. Academic data and history

| ID | Sev | Model says | Docs say | Fix |
| --- | --- | --- | --- | --- |
| J1 | M | Assignment/offering/division agreement stated in prose. Nothing stops one student having two enrolments in a term. | Divisions, offerings and assignments must agree on program and term. | F-J1 |
| J2 | m | No faculty load figures, no leave. | Spec §12: load caps (Theory 14, Lab 8, Tutorial 2); extra lectures count toward load; **information only**. Leave: nothing automatic. | F-J2 |
| J3 | m | `LectureOccurrence` kind/column agreement not enforced; no unique (entry, date); no division/batch copy for conflict queries. | An occurrence is one lecture on one date. | F-J3 |
| J4 | m | `AppliedChangeItem` snapshots omit mode and meeting link. | Mode and location are part of a lecture, and a revert must restore them. | F-J4 |
| J5 | M | `AppliedRecurringChangeItem` duplicates what `RecurringScheduleOverride` already carries. Override status ACTIVE/SUPERSEDED has no REVERTED; no meeting link. | Permanent change is a recurring overlay applied by the module "for now"; a revert applies to it too. | F-J5 |
| J6 | m | Most `ENUM` columns have no values listed. | Docs plan `shared/enums/` for value lists. | F-J6: every enum defined. |
| J7 | Deferred | No place for cross-division groups (PAS minor D2, FCTC D38). | The shared-course check and student lists are **skipped in version 1**. | F-J7: leave a hook, build nothing now. |

## 4. Decisions that already match

| Area | Model | Docs |
| --- | --- | --- |
| Original vs effective | Base timetable untouched; occurrences, applied changes, effective state | Overlay on top of the base timetable |
| Re-check on new version | Versions kept, `VERSION_UPDATE` run, reconciliation | `timetable-version-changes.md` |
| First approved wins, no reservation | Stated; no reservation table | `same-slot-tickets.md` |
| No overrides | No override column | `conflict-rules.md` |
| Review is optional, zero or more reviewers | `TicketReview` | `approval-model.md` |
| Acknowledge vs approve | `ACKNOWLEDGED` kept apart | CR/BR "review, acknowledge" |
| Cancel is an ordinary ticket | `CANCEL` | `cancellation.md` |
| Substitute keeps permanent assignment | Per-occurrence only | Spec §7 |
| Exchange atomic | One `AppliedChange`, several items | Spec §7 |
| Online has no room, no "--" | NULL room | `rooms-and-location.md` |
| Meeting link optional | Nullable | Same |
| In-app notifications only | No channel table | `triggers.md` |
| Issue never changes the timetable | Separate from tickets, link table | `issues-from-cr-and-br.md` |
| Issues never expire | No expiry column | Same |
| No ACR role | Roles CR and BR only | `permissions.md` |
| Permanent changes by the module, HOD only | Overrides table | `permanent-changes.md` |
| Room not auto-assigned | Nothing assigns rooms | Author picks, system suggests |
| Audit append-only with before/after | JSON states | `approval-model.md` |
| Batches inside divisions, composite FK | Yes | Spec §3 |
| Terms keep history | Division per term, enrolment history | Spec §12 |

## 5. Docs that the model would force to change

If you keep the model's approach on a point, these docs need an edit instead:

- A1: spec §4, §5 and `open-questions.md` item 1 (standalone decision).
- D4: `rooms-and-location.md` should say "location is shown as room or link; stored as `room_id` and `meeting_link`".
- A3: spec §5 event names vs outbox names, once settled.
- Open question 2 (permanent changes vs ERP) disappears if the module owns the base timetable, but only for the prototype.

## 6. Security reminder

The branch still carries a committed `.env` with MySQL passwords. Remove it from history and rotate them. This is separate from the design.
