# Lecture Rescheduling Module

> Status: **Draft spec** (ideation stage, no implementation yet)
> Last updated: 2026-10-27
> Source: owner's problem statement and POV notes, the spec review of 2026-10-16, and the EduPlusCampus timetable exports for SY-CSSE-B (AY 2026-27, Sem 1, V1, W.E.F. 06-Jul-2026).

## 1. Problem

College ERP timetables are static. When a faculty member needs to move, swap or add a lecture, the coordination happens outside the ERP, mostly on WhatsApp: faculty agree among themselves, then tell the CR/BR, who tell the students. Information ends up scattered, changes are easy to miss, and an extra lecture can take a long time to arrange.

## 2. Goal

Make **rescheduling, exchanging and adding extra lectures** quick and traceable, from request to the moment every affected person knows about it.

The module plugs into the existing college ERP but is **loosely coupled and independent**: it can be deployed, upgraded or switched off without changing other ERP modules.

## 3. Glossary

| Term | Meaning |
| --- | --- |
| Timetable (TNT) | The weekly schedule of a division for a semester, versioned by the ERP (e.g. V1, W.E.F. 06-Jul-2026). |
| Timetable entry (TTE) | One session in the timetable: faculty, subject, division or batch, type, room, day and slot(s). |
| Slot | One hourly cell in the grid (Mon to Fri, 08:00 to 20:00 in the current ERP). |
| Occurrence | One dated instance of a TTE, e.g. "OOP lab B2, Tue 03-Nov-2026, 10:00 to 12:00". |
| Session type | Theory (1 slot), Lab (2 consecutive slots, one batch), Tutorial (1 slot, one batch). |
| Division | A class, e.g. SY-CSSE-B. |
| Batch | A sub-group of a division for labs and tutorials, e.g. B1 to B5. |
| Ticket | A change request, modelled on a GitHub pull request. |
| Conflict | A rule violation found when checking a ticket, the equivalent of a merge conflict. |
| Change overlay | The set of approved changes this module stores on top of the ERP's base timetable. |
| CR / ACR | Class Representative / Assistant Class Representative (students). |
| BR | Batch Representative (student, one per batch). |

## 4. Scope

### In scope

- **Reschedule**: move one occurrence to another slot (and optionally another room).
- **Exchange**: two faculty swap occurrences.
- **Extra lecture**: add an occurrence that is not in the timetable.
- **Cancel**: drop an occurrence with no replacement.
- **Substitute**: another faculty takes an occurrence at the same time.
- Conflict checking, review and approval, notifications, audit log.
- A live view of the timetable (base timetable plus approved changes) for every role.

### Out of scope

- Creating or editing the base semester timetable (stays in the ERP).
- Assigning HODs, faculty allotment, user and role management (stays in the ERP).
- Attendance marking (the ERP attendance module consumes this module's events).

## 5. Ownership and integration with the ERP

The ERP keeps the base timetable. This module owns only the changes layered on top of it.

| | Owned by ERP | Owned by this module |
| --- | --- | --- |
| Users, roles, departments, divisions, batches | ✔ | read only |
| Subjects and faculty allotment | ✔ | read only |
| Base timetable (versions, W.E.F. dates) | ✔ | read only |
| Rooms | ✔ if present | extra attributes the ERP lacks |
| Academic calendar (holidays, exam weeks) | ✔ if present | otherwise kept here |
| Tickets, reviews, conflicts | | ✔ |
| Change overlay | | ✔ |
| Notifications and audit log | | ✔ |

**Rules**

- The module reads ERP data through an API or a scheduled sync, never by querying ERP tables directly. If EduPlusCampus has no API, the fallback is a scheduled import.
- The module never writes into ERP tables. It publishes events (`occurrence.moved`, `occurrence.cancelled`, `occurrence.added`, `occurrence.swapped`, `occurrence.substituted`) that other ERP modules, such as attendance, may subscribe to.
- Login is single sign-on against the ERP. ERP roles are mapped onto module roles.
- The module has its own database.
- If the ERP base timetable changes while tickets are pending, those tickets are re-checked for conflicts.
- If the ERP is unreachable, the module keeps serving the last synced timetable plus its own changes.

## 6. Roles and permissions

| Role | Who | Can do in this module |
| --- | --- | --- |
| Super Admin | Platform/module administrator | Configure rooms, slot grid, calendar, notification settings. Can override any ticket, but is not part of the routine approval chain. |
| Super HOD | Dean-level, above departments | Everything an HOD can, across all departments. Approves cross-department cases (e.g. shared rooms). |
| HOD | Head of a department | Sees all divisions of the department. Approves, rejects or overrides tickets in the department. Raises tickets on behalf of an absent faculty. Approves permanent changes. |
| Faculty | Teaching staff | Sees a personal timetable across divisions. Raises reschedule, exchange, extra, cancel and substitute tickets for occurrences they teach. Reviews tickets they are asked to review. |
| CR / ACR | Student representatives of a division | Sees the division timetable. Reviews or acknowledges tickets when asked. May request an extra lecture on behalf of the class. No assignment powers. |
| BR | Student representative of a batch | Same as CR, limited to one batch. |
| Student | Any student | Read-only access to the live timetable of their division, batch and shared courses. Receives notifications. |

Notes

- Assigning HODs and faculty happens in the ERP, not here. The original notes gave CR/ACR power to assign faculty, HODs and timetables; this spec removes that (see open question Q1).
- Roles still to be considered later: lab in-charge/assistant (confirms lab room and equipment), visiting faculty, and delegation when an HOD is on leave.

## 7. Change types

All changes are **single-occurrence** by default: they affect one dated occurrence, not the rest of the semester. A **permanent** change (every week from a date onward) is possible only through the HOD, and is recorded as a request for the ERP timetable owner to publish a new timetable version.

| Type | Input | Notes |
| --- | --- | --- |
| Reschedule | Source occurrence, target date + slot, optional room, optional description | A lab moves as a 2-slot block. Moving one batch never moves other batches in the same slot. |
| Exchange | Source occurrence, the other faculty's occurrence | The other faculty's consent is mandatory. Both moves succeed or fail together. A 2-hour lab cannot be swapped with a 1-hour lecture unless both sides fit. |
| Extra lecture | Division or batch, subject, date + slot(s), room | The slot must be free for the whole division or batch. |
| Cancel | Source occurrence, reason | No replacement. Students and attendance are notified. |
| Substitute | Source occurrence, substitute faculty | Same time and room. The substitute's consent is mandatory. |

## 8. User flows

### 8.1 Faculty reschedules a lecture (theory)

1. Faculty opens the module and sees their personal timetable.
2. Faculty clicks the edit icon on an occurrence and chooses **Reschedule**. This fixes the *from* side.
3. The timetable opens again, with free slots highlighted for that division. Faculty clicks the *to* slot. The module suggests suitable free rooms.
4. Faculty may add a description.
5. The module runs the conflict checks (section 10) and shows any blocks or warnings.
6. Faculty chooses how to proceed (section 9):
   - **Apply directly** (self-approval), or
   - **Request review** from any combination of: HOD, CR/BR, the faculty whose time is affected, other faculty.
7. Once the ticket is approved, the change overlay is updated and everyone concerned is notified (section 11).

### 8.2 Faculty reschedules a lab (practical)

Same as 8.1, with these differences:

- The occurrence belongs to one batch and spans two consecutive slots. The target must have both slots free.
- The target room must be a lab of the right kind (e.g. the DBMS lab is not the OOP lab).
- Other batches sharing the original slot are not affected.

### 8.3 Exchange

1. Faculty A picks their occurrence and chooses **Exchange**.
2. A picks faculty B's occurrence from the division timetable.
3. Conflict checks run for both moves.
4. B must accept. Other reviewers (HOD, CR) may be added as in 8.1.
5. On approval both moves apply atomically.

### 8.4 Extra lecture

1. Faculty (or CR on behalf of the class) chooses **Extra lecture**, picks division or batch, subject and a free slot.
2. The module suggests rooms that fit (type, seats, projector).
3. Review as in 8.1. A CR-initiated request needs the faculty's acceptance.

### 8.5 HOD

- An approval queue for the department, with pending tickets and open conflicts.
- A department-wide view of all changes this week.
- Acts for an absent faculty (raise, cancel, substitute).
- Approves permanent changes.

### 8.6 CR / BR

- Receives changes for their division or batch, acknowledges them, and reviews when asked.
- May request an extra lecture.

### 8.7 Student

- Sees the live timetable with changed occurrences marked.
- Receives notifications for every change that affects them, including shared courses from other divisions.

### 8.8 Super Admin

- Sets up rooms and their attributes, the slot grid, holidays and exam weeks, and notification settings.

## 9. Ticket lifecycle and approval

### States

`Draft → Open → (In review) → Approved → Applied`, with side exits `Rejected`, `Withdrawn`, `Expired`, and `Reverted` after `Applied`.

| State | Meaning |
| --- | --- |
| Draft | Being filled in, not visible to others. |
| Open | Submitted, conflict checks passed (or only warnings). |
| In review | Waiting on one or more requested reviewers. |
| Approved | All required approvals received; final conflict re-check runs. |
| Applied | Written to the change overlay; notifications sent. |
| Rejected | A reviewer rejected it, with a mandatory reason. The author can edit and resubmit. |
| Withdrawn | The author cancelled it before approval. |
| Expired | The occurrence's date passed with no decision. |
| Reverted | An applied change was undone. If the original slot has since been taken, the revert is itself checked for conflicts. |

### Approval model

Faculty can **apply a change on their own** (self-approval). They also have an **optional review step**: a checklist where they pick any combination of reviewers that fits the situation, such as:

- the HOD,
- the CR / BR,
- the faculty whose lecture slot they are taking,
- other faculty.

Rules that always apply, whatever the author picks:

- **Exchange** and **Substitute** always need the other faculty's acceptance.
- **Permanent** changes always need the HOD.
- A requested reviewer who does not respond within a timeout is escalated (e.g. to the HOD).
- Conflict checks run when the ticket is raised **and again at approval**, because the timetable may have changed in between.
- Every action is written to the audit log: who, what, when, and from which state.

Whether some situations (e.g. short notice) should force a review instead of allowing self-approval is still open (Q2).

## 10. Conflict rules ("merge conflicts")

Each check is either a **block** (the ticket cannot proceed) or a **warning** (shown, but the author can continue). Examples use SY-CSSE-B.

| Check | Example | Default |
| --- | --- | --- |
| Faculty already teaching | VNN moved to Tue 11:00, where VNN already has OOP lab B2 in B106 | Block |
| Division busy | Theory lecture moved to Thu 09:00, where SY-CSSE-B already has Data Structures | Block |
| Batch busy | B2's lab moved to a slot where B2 has a tutorial, even if other batches are free | Block |
| Shared-course students busy | PAS minor (MM0806, group D2) and FCTC (D38) include students from other divisions; all their timetables must be free | Block |
| Room occupied | Target room B107 already holds the DBMS lab | Block, and suggest a free room |
| Room unfit | Lab moved into a classroom, or seats below class strength | Block |
| No projector | Room lacks a projector the session needs | Warning |
| Two pending tickets on the same slot or room | Two faculty both request Fri 15:00 in B101 | First approved wins; the other is flagged and must pick again |
| Outside the calendar | Holiday, exam week, Sunday, outside 08:00 to 20:00 | Block |
| Faculty overload | Above the daily cap, or too many lectures in a row | Warning |
| Short notice | Change made a few hours before the occurrence | Warning (see Q2) |

A lab occupies two slots, so every check covers both hours.

## 11. Notifications

- **Who**: the author, requested reviewers, the HOD where relevant, CR/BR, and every affected student, including students from other divisions in shared courses.
- **When**: ticket raised, review requested, approved, rejected, expired, applied, reverted, plus a reminder before a moved occurrence.
- **Channels**: in-app first; email and push. SMS or WhatsApp are optional channels (the goal is to move coordination off WhatsApp, but students may still expect alerts there). Changes made at short notice use the loudest available channel.
- **Attendance**: a moved, cancelled or added occurrence emits an event so the ERP attendance module can follow it.

## 12. Data the module needs

| Data | Details | Seen in the ERP export |
| --- | --- | --- |
| Calendar | Semester start and end, holidays, exam weeks, working days | Timetable per AY and semester, with W.E.F. date |
| Timetable versions | Which version is current, and its W.E.F. date | Version V1 |
| Slot grid | Slot length, breaks, per-department differences | Hourly slots 08:00 to 20:00, Mon to Fri |
| Divisions and batches | Batch membership and strength | SY-CSSE-B with batches B1 to B5 |
| Shared courses | Student lists for minors and electives across divisions | PAS minor (D2), FCTC (D38) |
| Subjects | Type, duration, required room type, weekly hours | Theory, Lab (2 h), Tutorial |
| Faculty | Department, leave, daily and weekly load caps | Load: Theory 14, Lab 8, Tutorial 2 |
| Rooms | See below | B101, B105 to B108, B114; some sessions have room "--" |
| Contacts | Email, phone, notification preferences | Not in the export |

### Room attributes

| Attribute | Example |
| --- | --- |
| Building | A, B, C, D, E |
| Floor | 1 |
| Room number | 05 |
| Code (display) | B105 |
| Type | Classroom, Tutorial room, Lab (with lab kind) |
| Seats | 70 |
| Projector | Yes / No |
| Owning department | CSE |

Building, floor and number are stored as separate fields rather than parsed from the code, since parsing breaks on ground floors, basements and room numbers above 99.

## 13. Open questions

| # | Question | Current assumption |
| --- | --- | --- |
| Q1 | Should CR/ACR lose the power to assign faculty, HODs and timetables? | Yes. Assignment stays in the ERP; CR/ACR review and acknowledge only. Not yet explicitly confirmed by the owner. |
| Q2 | Should some situations force a review instead of allowing self-approval (short notice, exam weeks, shared courses, labs)? | Self-approval is always allowed; reviewers are optional except for exchange, substitute and permanent changes. |
| Q3 | Does EduPlusCampus expose an API or webhooks? | Unknown. Fallback is a scheduled import. |
| Q4 | What does room "--" mean in the ERP export (online, outdoors, unassigned)? | Treated as unassigned; room checks are skipped. |
| Q5 | Do extra lectures count toward faculty load, and is there a weekly cap? | Count toward load; no cap yet. |
| Q6 | What is the review timeout before escalation, and what counts as short notice? | To be decided (e.g. 24 h timeout, under 4 h is short notice). |
| Q7 | Are the abbreviations correct: TNT = timetable, TTE = timetable entry, BR = Batch Representative, ACR = Assistant CR? | Yes, assumed throughout. |

### Decisions taken

| Decision | Outcome |
| --- | --- |
| Reschedule scope | One date by default; permanent changes only through the HOD. |
| Faculty self-approval | Allowed, with an optional reviewer checklist (HOD, CR, affected faculty, any combination). |
| Base timetable owner | The ERP. The module stores only the change overlay. |
| Students | Users with read-only access to the live timetable. |
