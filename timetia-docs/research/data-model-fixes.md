# Proposed data model fixes

- **Type:** research-note
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-09

The fixes for every finding in [data-model-audit.md](data-model-audit.md). Design only, no code. Table and column names follow the original model. Anything not mentioned stays as in the original.

**Net effect:** 40 tables become 43. Added: `TimeSlot`, `LabKind`, `SubjectComponent`, `FacultyLoadProfile`, `OccurrenceClaim`. Removed: `CurrentTimetableVersion` (replaced by W.E.F. date), `AppliedRecurringChangeItem` (merged into the override). Optional later: `FacultyLeave`, `ExternalRef`.

## 1. Enums, defined once

Docs plan these under `shared/enums/`. Every `ENUM` in the model gets these values.

| Enum | Values |
| --- | --- |
| `record_status` (Department, Program, Subject, Division, Batch, Room, Faculty) | ACTIVE, INACTIVE |
| `student_status` | ACTIVE, INACTIVE, GRADUATED |
| `term_status` | UPCOMING, ACTIVE, COMPLETED |
| `account_status` | ACTIVE, INACTIVE, SUSPENDED |
| `degree_level` | BTECH, MTECH, DIPLOMA, OTHER (replaces VARCHAR) |
| `session_type` | THEORY, LAB, TUTORIAL |
| `room_type` | CLASSROOM, TUTORIAL_ROOM, LAB |
| `building` | A, B, C, D, E |
| `lecture_mode` | IN_PERSON, ONLINE (was OFFLINE/ONLINE) |
| `day_type` | WORKING_DAY, HOLIDAY, EXAM_WEEK, SUNDAY_OFF |
| `version_status` | DRAFT, PUBLISHED, ARCHIVED |
| `ticket_type` | RESCHEDULE, EXCHANGE, EXTRA, CANCEL, SUBSTITUTE, PERMANENT_CHANGE, REVERT |
| `ticket_status` | DRAFT, IN_REVIEW, APPLIED, REJECTED, WITHDRAWN, EXPIRED, REVERTED |
| `approval_path` | SELF, REVIEWED |
| `operation_type` (one-off) | MOVE, CREATE_EXTRA, CANCEL, REASSIGN_FACULTY |
| `recurring_operation` | ADD, REPLACE, CANCEL |
| `reviewer_capacity` | HOD, CR, BR, AFFECTED_FACULTY, OTHER_FACULTY |
| `review_status` | PENDING, APPROVED, REJECTED, ACKNOWLEDGED |
| `conflict_type` | FACULTY_BUSY, DIVISION_BUSY, BATCH_BUSY, ROOM_OCCUPIED, ROOM_UNFIT, SAME_SLOT_TICKET, OUTSIDE_CALENDAR, OUTSIDE_SLOT_GRID. (Later: SHARED_COURSE_BUSY.) |
| `check_trigger` | SUBMISSION, APPLY, VERSION_UPDATE (+ REVERT_CHECK) |
| `check_result` | CLEAR, BLOCKED |
| `override_status` | ACTIVE, SUPERSEDED, REVERTED |
| `claim_kind` | FACULTY, ROOM, STUDENT_BATCH |
| `notification_type` | TICKET_RAISED, REVIEW_REQUESTED, REVIEW_APPROVED, REVIEW_REJECTED, TICKET_EXPIRED, TICKET_APPLIED, TICKET_REVERTED, TICKET_CONFLICTED, ISSUE_MENTION |
| `outbox_event_type` | OCCURRENCE_MOVED, OCCURRENCE_CANCELLED, OCCURRENCE_ADDED, OCCURRENCE_SWAPPED, OCCURRENCE_SUBSTITUTED |
| `delivery_status` | PENDING, DELIVERED, FAILED |
| `reconciliation_result` | MATCHED, REMOVED, AMBIGUOUS, REQUIRES_REVIEW |

## 2. Fixes

### F-A1 Standalone vs ERP (decided: standalone)

If you keep the standalone prototype:

1. Done: standalone version 1 is [ADR 0001](../modules/lecture-rescheduling/decisions/0001-standalone-version-1.md).
2. Treat Module A (Department to AcademicCalendarDay, UserAccount, Role) as the **reference-data boundary**. The workflow modules (B to H) reference these tables only by their keys, never by anything ERP-specific.
3. For later ERP sync add one table, not columns:

| Table | Columns |
| --- | --- |
| `ExternalRef` (later) | `entity_type` ENUM, `entity_id` BIGINT, `source_system` VARCHAR(30), `external_id` VARCHAR(80), `synced_at`; PK (`entity_type`, `entity_id`, `source_system`); UNIQUE (`entity_type`, `source_system`, `external_id`) |

That keeps the original model's wish of "no `erp_*` columns" and meets the docs' "scheduled import" fallback.

If you go back to ERP-coupled: drop `password_hash`, mark Module A tables read only (filled by import), drop timetable authoring, keep Modules C to H unchanged.

### F-A2 Authentication

`UserAccount`: add `auth_provider` ENUM(LOCAL, ERP_SSO) DEFAULT LOCAL; `password_hash` becomes NULL allowed with CHECK (`auth_provider`='ERP_SSO' OR `password_hash` IS NOT NULL). Prototype uses LOCAL, ERP later flips to ERP_SSO with no table change.

### F-B1 Ticket status

`TimetableChangeTicket.status` uses the new `ticket_status`. Rules:

| From | To | When |
| --- | --- | --- |
| DRAFT | APPLIED | self-approval, no conflicts |
| DRAFT | IN_REVIEW | sent for review |
| IN_REVIEW | APPLIED | all requested approvals in, no conflicts |
| IN_REVIEW | REJECTED | a reviewer rejects, reason required |
| DRAFT / IN_REVIEW | WITHDRAWN | author withdraws |
| DRAFT / IN_REVIEW | EXPIRED | lecture starts first |
| REJECTED | DRAFT | author edits and resubmits (decided 2026-11-09); `revision_no` goes up by 1 |
| APPLIED | REVERTED | a revert ticket is applied |

REJECTED is therefore not final: it returns to DRAFT when the author edits. `APPROVED` and `OPEN` do not exist. A review decision is `TicketReview.review_status`, not a ticket state.

### F-B2 Revert as a new ticket

`TimetableChangeTicket` adds `reverts_ticket_id` (self FK, NULL allowed) and `reverted_by_ticket_id` (self FK, NULL allowed). CHECK: `ticket_type`='REVERT' iff `reverts_ticket_id` IS NOT NULL. UNIQUE (`reverts_ticket_id`) so an applied ticket is reverted once.

`AppliedChange`: drop `action_type`; keep `reverses_applied_change_id`. A revert ticket creates its own `AppliedChange` whose items are the mirror of the original. When it applies, the original ticket becomes REVERTED and `reverted_by_ticket_id` is set (same transaction). The revert goes through the normal conflict check (see `rules/reverts.md`), so it can be BLOCKED if the original slot was taken. Authorised to raise (decided 2026-11-08): ticket author, the affected faculty, the HOD; only while the lecture has not started.

### F-B3 Approval path, reviewer capacity, on behalf, escalation

`TimetableChangeTicket` adds:

| Column | Type | Meaning |
| --- | --- | --- |
| `approval_path` | ENUM | SELF or REVIEWED; set when applied |
| `acting_faculty_id` | FK Faculty, NULL | Set when a HOD raises for an absent faculty. The author is the HOD; the lecture's faculty is recorded here. |
| `consent_requested` | BOOLEAN | For EXCHANGE/SUBSTITUTE: did the author ask the other faculty. Informational. |

`TicketReview` adds `reviewer_capacity` (enum above), `is_escalation` BOOLEAN DEFAULT FALSE, `escalated_from_review_id` (self FK, NULL). See the unique key below. Rules (app plus triggers):

- A CR reviewer must hold a CR assignment for the division of the ticket and the ticket must be THEORY; a BR must hold the batch and the ticket must be LAB or TUTORIAL.
- Rejection by anyone sets the ticket REJECTED. Acknowledged never blocks. Applied needs every requested APPROVED.
- **Edit and resubmit (decided 2026-11-09):** the same ticket goes REJECTED to DRAFT to IN_REVIEW. `TimetableChangeTicket` adds `revision_no` SMALLINT DEFAULT 1. `TicketReview` adds `ticket_revision_no`; UNIQUE (`ticket_id`, `ticket_revision_no`, `reviewer_user_id`, `reviewer_capacity`), so earlier rejections stay visible as history and only reviews of the current revision count toward "all approvals in". Edited items are not versioned in their own table; the audit event for the resubmission stores the before/after JSON.
- **Escalation:** after a rejection the author may add the HOD as a reviewer with `is_escalation`=TRUE and `escalated_from_review_id` pointing to the rejection. The ticket goes REJECTED to IN_REVIEW directly for that case.
- The extra `resubmission_of_ticket_id` column is not needed.

### F-B4 Expiry and conflicted flag

`TimetableChangeTicket` adds `expires_at` TIMESTAMP (earliest start of any affected occurrence, from the current effective state; recalculated on edit), `is_conflicted` BOOLEAN DEFAULT FALSE, `conflicted_since` TIMESTAMP NULL. A scheduled job sets EXPIRED when `expires_at` has passed and the status is DRAFT/IN_REVIEW. `is_conflicted` is set by a `ConflictCheckRun`; applying is blocked while it is TRUE (checked inside the apply transaction, not trusted from the flag alone). Setting it creates a TICKET_CONFLICTED notification for the author and the HOD.

### F-B5 Mandatory reasons

CHECK on `TicketChangeItem`: `operation_type`<>'CANCEL' OR `reason` IS NOT NULL. CHECK on `TicketReview`: `review_status`<>'REJECTED' OR `decision_reason` IS NOT NULL. Ticket `description` stays optional.

### F-B6 Who can author, permanent needs HOD

- Application rule plus trigger: ticket `author_user_id` must have FACULTY profile, or an active HOD/SUPER_HOD assignment (with `acting_faculty_id`). CR/BR/Student cannot author.
- Permanent: a trigger on moving a PERMANENT_CHANGE ticket to APPLIED requires one `TicketReview` with `reviewer_capacity`='HOD' and `review_status`='APPROVED'. `approval_path` must be REVIEWED for this type.

### F-B7 Change item rules

`TicketChangeItem` adds `exchange_group` TINYINT NULL (both MOVE items of an exchange share a value) and `target_location_mode` is the existing `target_mode`. Required columns per operation, enforced by CHECK:

| Operation | Required | Must be NULL |
| --- | --- | --- |
| MOVE | `source_occurrence_id`, `target_date`, `target_start_time`, `target_end_time`, `target_mode`; plus `target_room_id` when mode is IN_PERSON | `target_faculty_id`, `teaching_assignment_id` |
| CREATE_EXTRA | `teaching_assignment_id`, same target columns | `source_occurrence_id`, `target_faculty_id` |
| CANCEL | `source_occurrence_id`, `reason` | all `target_*` |
| REASSIGN_FACULTY | `source_occurrence_id`, `target_faculty_id` | `target_date`, `target_*_time`, `target_room_id`, `target_mode` (time and room are kept) |

Ticket-level rules: EXCHANGE has exactly two MOVE items sharing `exchange_group` and the source occurrences belong to different faculty; both end at the other's start time and the durations must fit. A 2-hour lab and a 1-hour lecture need both sides to fit. RESCHEDULE has one MOVE; EXTRA one or more CREATE_EXTRA; CANCEL one or more CANCEL; SUBSTITUTE one or more REASSIGN_FACULTY. PERMANENT_CHANGE has no one-off items (it uses `TicketRecurringChangeItem`). REVERT items mirror the applied items they undo.

### F-B8 Timestamps

`updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP` on every table that has it. Ticket adds `submitted_at` (when it left DRAFT) and `closed_at` (terminal state time).

### F-C1 No warning level

`ConflictFinding`: drop `severity`. Every row is a blocker. `ConflictCheckRun.result` is CLEAR or BLOCKED. The three plain-information items (no projector, load above cap, short notice) are **not stored**. They are computed when a ticket is shown, from `SubjectComponent.needs_projector`, `Room.has_projector`, `FacultyLoadProfile` and the ticket time against the lecture time. They never block and need no acknowledgement.

### F-C2 Conflict types and references

`ConflictFinding.conflict_type` uses the enum in section 1. Add three nullable references:

| Column | Used by |
| --- | --- |
| `conflicting_ticket_id` (FK ticket) | SAME_SLOT_TICKET: the ticket that was applied first |
| `calendar_day_id` (FK AcademicCalendarDay) | OUTSIDE_CALENDAR |
| `room_id` (FK Room) | ROOM_OCCUPIED, ROOM_UNFIT |

Add `is_resolved` BOOLEAN DEFAULT FALSE and `resolved_at`. A re-check supersedes: the latest run for the ticket is the truth; older runs are history.

### F-C3 Findings for permanent changes

`ConflictFinding` adds `recurring_change_item_id` (FK, NULL). CHECK exactly one of `change_item_id`, `recurring_change_item_id` is set, or neither for ticket-wide findings. A permanent change is checked for every generated date from `effective_from`.

### F-C4 Make "first approved wins" a database guarantee

New table `OccurrenceClaim`. One row per **hour cell** an effective, non-cancelled occurrence occupies, per resource.

| Column | Type | Notes |
| --- | --- | --- |
| `claim_id` | BIGINT UNSIGNED | PK |
| `claim_kind` | ENUM | FACULTY, ROOM, STUDENT_BATCH |
| `resource_id` | BIGINT UNSIGNED | faculty_id, room_id, or batch_id |
| `claim_date` | DATE | |
| `cell_hour` | TINYINT UNSIGNED | 8 to 19 (hour the lecture covers; a lab writes two rows) |
| `occurrence_id` | BIGINT UNSIGNED | FK LectureOccurrence |
| `created_at` | TIMESTAMP | |

UNIQUE (`claim_kind`, `resource_id`, `claim_date`, `cell_hour`). CHECK `cell_hour` BETWEEN 8 AND 19.

- A division-wide theory lecture writes one STUDENT_BATCH row per batch of the division. A batch session writes only its batch. So "division vs batch" and "batch vs batch" clashes fall out of the same key, and different batches in the same cell stay allowed.
- In-person lectures write ROOM rows. Online lectures write none ("room checks are skipped").
- Applying a ticket releases the old claims and inserts the new ones inside the same transaction as `OccurrenceEffectiveState`. If two tickets race, the second hits the unique key, rolls back, and is reported as a SAME_SLOT_TICKET conflict. No overrides are possible.
- Hour-based cells are grid independent, so faculty teaching under different department grids are still compared correctly.
- The claims are a derived table; `OccurrenceEffectiveState` plus applied history stays the source of truth and the claims can be rebuilt.

### F-C5 Check triggers

`ConflictCheckRun.trigger`: SUBMISSION (raised), APPLY (inside the apply transaction, replaces APPROVAL and PRE_APPLICATION), VERSION_UPDATE (new timetable version arrived). Revert tickets use SUBMISSION and APPLY like any other.

### F-D1 Room

| Column | Change |
| --- | --- |
| `building` | ENUM A to E replaces `building_name` VARCHAR |
| `floor` | new, TINYINT (allows 0 and negatives for ground and basement) |
| `room_number` | new, VARCHAR(10) (not numeric: allows "105", "G01") |
| `room_code` | keep as the display code (B105); GENERATED from building, floor, number |
| `room_type` | CLASSROOM, TUTORIAL_ROOM, LAB |
| `lab_kind_id` | new, FK `LabKind`, required when `room_type`='LAB', else NULL |
| `owning_department_id` | new, FK Department, NULL allowed |
| `capacity` | keep (the docs call it "seats") |

UNIQUE (`building`, `floor`, `room_number`). New small table `LabKind(lab_kind_id, kind_code UNIQUE, kind_name)`, for example DBMS_LAB, OOP_LAB.

### F-D2 What a session needs

New table `SubjectComponent` (one row per subject and session type):

| Column | Type | Notes |
| --- | --- | --- |
| `subject_component_id` | BIGINT UNSIGNED | PK |
| `subject_id` | FK Subject | |
| `session_type` | ENUM | THEORY, LAB, TUTORIAL |
| `required_room_type` | ENUM | CLASSROOM, TUTORIAL_ROOM, LAB |
| `required_lab_kind_id` | FK LabKind, NULL | required when room type is LAB |
| `slots_per_session` | TINYINT | THEORY 1, LAB 2, TUTORIAL 1 (a theory block may be longer) |
| `weekly_hours` | DECIMAL(3,1) | |
| `needs_projector` | BOOLEAN | feeds the information-only projector note |

UNIQUE (`subject_id`, `session_type`). `TeachingAssignment` gets `subject_component_id` (replacing the loose `session_type`; keep a copied `session_type` with a composite FK for the batch rules). This is what ROOM_UNFIT reads (type, lab kind, seats against division or batch strength) and what the lab "two slots" rule reads. It also makes the "TimetableEntry duration" check exact: interval length = `slots_per_session` hours for lab and tutorial.

### F-D3 Mode and room

Rename `OFFLINE` to `IN_PERSON`. Add the same CHECK everywhere a mode and room appear: `mode`='IN_PERSON' IFF `room_id` IS NOT NULL, on `TimetableEntry`, `RecurringScheduleOverride`, `TicketRecurringChangeItem` (when set), `TicketChangeItem` (target, for MOVE and CREATE_EXTRA), `OccurrenceEffectiveState`, and the after-columns of `AppliedChangeItem`. This enforces "every changed in-person lecture has a room, even if the original had none". The importer maps ERP "--" to ONLINE. The meeting link stays optional.

### F-D4 Location

Keep `room_id` + `meeting_link`. Docs wording to adopt: "Location is shown as the room number for in-person, or the link for online; it is stored in two columns so rooms can be joined."

### F-E1 Slot grid

New table `TimeSlot`:

| Column | Type |
| --- | --- |
| `time_slot_id` | BIGINT UNSIGNED PK |
| `department_id` | FK Department, NULL means the default grid |
| `slot_no` | TINYINT |
| `start_time`, `end_time` | TIME |
| `is_break` | BOOLEAN |

UNIQUE (`department_id`, `slot_no`); the default grid uses a fixed department value (for example 0) or a generated key, because MySQL treats NULLs as distinct in unique keys. Default grid 08:00 to 20:00 hourly. `TimetableEntry`, `TicketChangeItem` and the overrides keep `start_time` and `end_time` but the app validates they sit on the grid. CHECK `start_time` >= '08:00' AND `end_time` <= '20:00'. Super Admin edits the grid (spec §8.8).

### F-E2 Calendar and weekday

`AcademicCalendarDay`: drop `teaching_allowed`; "teachable" is derived from `day_type`='WORKING_DAY'. Sundays are `SUNDAY_OFF` rows or simply `day_of_week` 7 rejected by the check. `TimetableEntry.day_of_week` CHECK BETWEEN 1 AND 6 (Mon to Sat; ERP uses Mon to Fri; keep Sat available). Exam weeks are per program; if you need that, add `program_id` NULL to the calendar and make the unique key (`term_id`, `program_id`, `calendar_date`).

### F-F1 Versions have a W.E.F. date

`TimetableVersion` adds `effective_from` DATE NOT NULL (W.E.F.). UNIQUE (`division_id`, `effective_from`) for PUBLISHED versions (use a generated column that is NULL for DRAFT/ARCHIVED). The version in force on a date D is the PUBLISHED version of the division with the greatest `effective_from` <= D. **Drop `CurrentTimetableVersion`.** Occurrence generation for a date uses that rule; occurrences before the W.E.F. of a newer version stay linked to the old version's entries, occurrences from the W.E.F. onward are generated or reconciled against the new one. `OccurrenceReconciliation` stays.

### F-F2 Immutability and reconciliation

- Trigger: no INSERT/UPDATE/DELETE on `TimetableEntry` when its version is PUBLISHED or ARCHIVED; no UPDATE of version fields other than status ARCHIVED.
- `OccurrenceReconciliation` adds `resolved_by_user_id`, `resolved_at` for REQUIRES_REVIEW and AMBIGUOUS rows, and a composite FK so both versions belong to the same division.
- A new version also triggers `VERSION_UPDATE` checks for active overrides: check `RecurringScheduleOverride` rows against the new version and flag them to the author and the HOD.

### F-G1 Role scope

`Role` adds `scope_type` ENUM(GLOBAL, DEPARTMENT, DIVISION, BATCH):

| role_code | scope_type | May act on |
| --- | --- | --- |
| SUPER_ADMIN | GLOBAL | configuration only |
| SUPER_HOD | GLOBAL | all departments |
| HOD | DEPARTMENT | own department |
| CR | DIVISION | THEORY tickets and issues of the division |
| BR | BATCH | LAB and TUTORIAL tickets and issues of the batch |
| FACULTY, STUDENT | (see F-G2) | |

`UserRoleAssignment` adds `scope_type` (copied) with composite FK (`role_id`, `scope_type`) to `Role(role_id, scope_type)` and a CHECK that exactly the matching scope column is set: DEPARTMENT needs `department_id` only, DIVISION needs `division_id` only, BATCH needs `batch_id` only, GLOBAL needs none. Multiple CRs per division are allowed (no ACR), as decided. The "CR theory only, BR lab and tutorial" rule is a check at review/issue time against `TeachingAssignment.session_type`.

### F-G2 Single source of truth, one HOD

Remove STUDENT and FACULTY from `Role`; they are the `Student` and `Faculty` profiles. UNIQUE on active HOD per department using a generated column (`department_id` where `valid_until` IS NULL and role is HOD). Roles tied to a term-specific division expire with the term: set `valid_until` to the term end when the term completes.

### F-H1 Issue scope, closer, faculty mentions

`Issue` adds: `division_id` (FK, NULL), `batch_id` (FK, NULL), `opened_as_assignment_id` (FK UserRoleAssignment, NOT NULL), `session_type` ENUM THEORY/LAB/TUTORIAL NOT NULL, `closed_by_user_id` (FK, NULL). CHECK exactly one of `division_id`, `batch_id`; THEORY pairs with division and CR, LAB/TUTORIAL with batch and BR. CHECK `status`='CLOSED' IFF `closed_at` and `closed_by_user_id` are set. The closer must be the opener or a mentioned faculty (app rule, trigger optional). No expiry columns.

`IssueMention`: replace `mentioned_user_id` with `mentioned_faculty_id` (FK Faculty); UNIQUE (`issue_id`, `mentioned_faculty_id`). `IssueTicketLink` keeps its composite PK and adds `linked_by_user_id`.

### F-I1 Notifications

`notification_type` uses the enum (no free text). Add `issue_id` FK NULL. CHECK at least one of `ticket_id`, `issue_id`, `occurrence_id` is set. Per-recipient rows stay (in-app only, no reminders, no channel or delivery columns). Recipients per docs: author, requested reviewers, mentioned faculty, HOD where relevant, CR/BR in scope, affected students of the division or batch. Students of other divisions in shared courses are not covered in version 1. Index (`recipient_user_id`, `read_at`, `created_at`).

### F-I2 Outbox

`event_type` uses the five names. Add `aggregate_type` ENUM(OCCURRENCE, APPLIED_CHANGE) so `aggregate_id` is unambiguous. `delivery_status`: keep `published_at`. An exchange emits OCCURRENCE_SWAPPED, a substitute OCCURRENCE_SUBSTITUTED, a revert emits the event that undoes the original. Written in the same transaction as the apply.

### F-I3 Audit

`TimetableAuditEvent`: `action_type` becomes ENUM (TICKET_SUBMITTED, REVIEW_REQUESTED, REVIEW_DECIDED, TICKET_APPLIED, TICKET_REJECTED, TICKET_WITHDRAWN, TICKET_EXPIRED, TICKET_REVERTED, CONFLICT_FOUND, VERSION_PUBLISHED, ISSUE_OPENED, ISSUE_CLOSED, ROLE_ASSIGNED, ROLE_ENDED). Add `from_status` and `to_status` as plain columns (the JSON `previous_state`/`new_state` stays for detail), plus `issue_id` and `timetable_version_id` FKs. Enforce append-only: the application account has INSERT and SELECT only on this table; a trigger rejects UPDATE and DELETE.

### F-J1 Make agreement a key, not a rule

- `Division`: UNIQUE (`division_id`, `program_id`, `term_id`).
- `SubjectOffering`: UNIQUE (`subject_offering_id`, `program_id`, `term_id`).
- `TeachingAssignment`: add `program_id`, `term_id`; composite FK (`division_id`, `program_id`, `term_id`) to Division and (`subject_offering_id`, `program_id`, `term_id`) to SubjectOffering. A mismatch is then impossible.
- Scope-key uniqueness with a nullable batch: generated column `batch_scope` = COALESCE(`batch_id`, 0); UNIQUE (`subject_offering_id`, `session_type`, `division_id`, `batch_scope`). CHECK matrix: THEORY batch NULL; LAB and TUTORIAL batch NOT NULL.
- `StudentEnrollment`: add `term_id` with composite FK to Division; UNIQUE (`student_id`, `term_id`) so a student cannot be in two divisions in a term. Keep `enrolled_at`/`ended_at` for mid-term moves only if you need them; otherwise drop `ended_at`.

### F-J2 Faculty load (information) and leave

New table `FacultyLoadProfile`: `faculty_id` FK, `term_id` FK, `theory_hours`, `lab_hours`, `tutorial_hours` (e.g. 14, 8, 2), `daily_cap_hours` NULL, `max_consecutive` NULL. PK (`faculty_id`, `term_id`). Extra occurrences count toward load, computed from `OccurrenceEffectiveState` and `occurrence_kind`. Nothing enforces it, nothing blocks. `FacultyLeave` is optional and not needed for rules (nothing happens automatically on leave); add later if the HOD view wants it.

### F-J3 Occurrence integrity

`LectureOccurrence` adds `division_id` and `batch_id` (copied from the assignment; composite FKs keep them honest) so conflict queries do not join four tables. UNIQUE (`timetable_entry_id`, `occurrence_date`). CHECK by kind: BASE needs `timetable_entry_id`; EXTRA needs `created_by_ticket_id` and no entry; PERMANENT needs `recurring_override_id`. `record_status` values: ACTIVE, SUPERSEDED (replaced by reconciliation); cancellation lives only in `OccurrenceEffectiveState.is_cancelled`, so there is one cancellation source.

### F-J4 Applied item snapshots

`AppliedChangeItem` adds `before_mode`, `after_mode`, `before_meeting_link`, `after_meeting_link`. Reverts then restore everything a lecture shows.

### F-J5 Permanent changes

Merge `AppliedRecurringChangeItem` into `RecurringScheduleOverride`: add `applied_change_id` (FK AppliedChange NOT NULL), `reverted_by_applied_change_id` (FK NULL), `meeting_link`, and `override_status` ACTIVE, SUPERSEDED, REVERTED. Drop `AppliedRecurringChangeItem`. A permanent change takes effect only after a HOD APPROVED review (F-B6). Applying it regenerates affected future occurrences and closes the earlier ones exactly as a one-off change would (claims updated in the same transaction). The tension in `permanent-changes.md` (module and ERP showing different weekly timetables) disappears in the standalone prototype; it comes back only if you choose ERP-coupled.

### F-J6 Enums

See section 1. Every enum is defined; add them to `shared/enums/` in the docs when you migrate.

### F-J7 Shared courses (deferred)

Do not build for version 1. The hook, if you want it later: `SharedCourseGroup(group_id, subject_offering_id, group_code)`, `SharedCourseMember(group_id, student_id)` and a nullable `shared_group_id` on `TeachingAssignment`. Then add the conflict type `SHARED_COURSE_BUSY`, which would extend `OccurrenceClaim` with `claim_kind`=STUDENT. Nothing in this design blocks that.

## 3. Resulting table inventory (43)

| Module | Tables |
| --- | --- |
| A Reference data | Department, Program, AcademicTerm, UserAccount, Role, UserRoleAssignment, Student, Faculty, Subject, **SubjectComponent**, Division, Batch, StudentEnrollment, SubjectOffering, TeachingAssignment, Room, **LabKind**, AcademicCalendarDay, **TimeSlot**, **FacultyLoadProfile** |
| B Base timetable | TimetableVersion, RecurringEntry, TimetableEntry |
| C Occurrences | LectureOccurrence, OccurrenceReconciliation |
| D Requests | TimetableChangeTicket, TicketChangeItem, TicketReview |
| E Permanent changes | TicketRecurringChangeItem, RecurringScheduleOverride |
| F Conflicts | ConflictCheckRun, ConflictFinding |
| G Applied state | AppliedChange, AppliedChangeItem, OccurrenceEffectiveState, **OccurrenceClaim** |
| H Supporting | TimetableAuditEvent, Notification, OutboxEvent, Issue, IssueMention, IssueTicketLink |

Bold means new. Later: ExternalRef, FacultyLeave, shared-course tables.

## 4. Updated apply transaction

1. Lock the ticket row and the affected occurrences' `OccurrenceEffectiveState` rows (`state_revision` compare).
2. Re-run the conflict check (trigger APPLY). Any finding aborts and sets `is_conflicted`.
3. For REVERT tickets, check the lecture has not started.
4. Create `AppliedChange` and items (with mode and link snapshots).
5. Update `OccurrenceEffectiveState`.
6. Replace `OccurrenceClaim` rows; a unique-key failure aborts and is reported as SAME_SLOT_TICKET.
7. Set ticket APPLIED (and the original REVERTED for a revert), set `approval_path`, `closed_at`.
8. Write audit, outbox event and notifications.

All or nothing, as in the original.
