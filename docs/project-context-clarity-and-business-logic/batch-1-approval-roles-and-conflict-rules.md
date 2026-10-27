# Batch 1: Approval, Roles and Conflict Rules

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

This is the first batch of business-logic decisions for the Lecture Rescheduling module. It records what the owner decided in the design discussion of 2026-10-27, together with the decisions that were already taken earlier. Where a decision here differs from [`../modules/lecture-rescheduling.md`](../modules/lecture-rescheduling.md), **this document wins** and the module spec is to be updated when the docs are migrated (see [`../STRUCTURE.md`](../STRUCTURE.md)).

Batch 2 ([reverts, same-slot tickets, cancellation and requests](batch-2-reverts-same-slot-tickets-cancellation-and-requests.md)) has since been decided; its questions are listed in section 8 only as history.

## 1. Context in one paragraph

College ERP timetables are static, so faculty arrange changes on WhatsApp. This module adds a pull-request-like **ticket** flow on top of the ERP timetable: a faculty member picks a *from* lecture and a *to* slot, runs a conflict check, then either applies the change themselves or asks others to review. Everyone affected is notified. The ERP stays the owner of the base timetable; the module stores only a **change overlay**. The module is loosely coupled and independent.

## 2. Terms used here

| Term | Meaning |
| --- | --- |
| Occurrence | One lecture on one specific date. The ERP timetable is a weekly pattern; an occurrence is a single dated instance of it, e.g. "OOP lab B2, Tue 03-Nov-2026, 10:00 to 12:00". Every change in this module works on one occurrence unless it is a permanent change. |
| Ticket | A change request, modelled on a pull request. |
| Conflict | A rule violation found while checking a ticket, the equivalent of a merge conflict. |
| Self-approval | The author applies their own ticket without any reviewer. |
| CR | Class Representative. A division can have **several** CRs. Covers **theory** lectures only. |
| BR | Batch Representative. Covers **lab and tutorial** lectures of their batch. |

## 3. Decisions carried over from earlier

| Decision | Outcome |
| --- | --- |
| Reschedule scope | One occurrence by default. A permanent change (every week from a date) goes only through the HOD. |
| Faculty self-approval | Allowed, with an optional reviewer checklist: HOD, CR/BR, the affected faculty, other faculty, in any combination. |
| Base timetable owner | The ERP. The module stores only the change overlay. |
| Students | Read-only users of the live timetable. They receive notifications. |
| Re-check | Conflicts are checked when the ticket is raised and again at approval. |
| Audit | Every action is logged: who, what, when, from which state. |

## 4. Approval

### 4.1 Self-approval is always allowed

A faculty member can apply their own change directly. **No case forces a review**, whether the change is at short notice, in an exam week, or involves a shared course. A review is always the author's choice.

### 4.2 Consent for exchange and substitute is optional

Faculty have the right to **exchange** and **substitute** lectures. Asking the other faculty for consent is optional, because they have often already agreed in person.

| Situation | What happens |
| --- | --- |
| Author asks for consent | The other faculty accepts or rejects as a reviewer. |
| Author does not ask | The change applies once the conflict check passes. The other faculty is **notified** and has a **right to revert** it before the lecture. |

This replaces the spec rule that exchange and substitute "always need the other faculty's acceptance". An exchange still applies both moves together or neither. Rescheduling into a slot where the students have no lecture is allowed, as before.

The exact limits of the revert right (who can revert, what happens if the original slot was taken) belong to batch 2.

### 4.3 Permanent changes

Still require the HOD. This is the one case where approval cannot be skipped.

### 4.4 Review timeout and expiry

There is **no escalation**. If a requested reviewer does not respond, the ticket waits. It **expires when the lecture starts**. This is stricter than the spec, which said the ticket expires when the date passes.

### 4.5 Rejection

- A faculty reviewer, the HOD, and a CR or BR within their scope (section 5) can reject. A rejection needs a reason.
- The author can **escalate a rejection to the HOD**.
- The author can also edit and resubmit.

## 5. Roles and powers

| Role | Powers in this module |
| --- | --- |
| Super Admin | Configures rooms, slot grid, calendar and notification settings. Not part of routine approval. |
| Super HOD | Everything an HOD can, across departments. |
| HOD | Sees the department. Approves and rejects tickets. Handles escalated rejections. Raises tickets for an absent faculty. Approves permanent changes. |
| Faculty | Raises reschedule, exchange, extra, cancel and substitute tickets for lectures they teach. Reviews tickets they are asked to review. Can revert an exchange or substitute applied to their lecture without their consent. |
| CR | Review, acknowledge and request extra lectures for **theory** lectures of the division. May reject theory tickets they review. |
| BR | Same powers as a CR, for **lab and tutorial** lectures of their batch. |
| Student | Read-only timetable and notifications. |

### CR and ACR

- **CR/ACR have no assign powers.** The original notes let them assign faculty, HODs and timetables. That is removed: assignment stays in the ERP.
- A class can have **multiple CRs** instead of "CR plus ACR". This is the current decision and the CR/ACR split may be revisited later.
- CR is for theory only. Labs and tutorials belong to the BR, because they are batch sessions.

## 6. Conflicts

### 6.1 A conflict always blocks

There is **no warning level**. A ticket cannot be accepted until every conflict is resolved, the same way a merge cannot complete with unresolved conflicts. The author fixes the plan (another slot, room or date) or withdraws.

Every check in section 10 of the module spec is therefore a **block**:

| Check | Example |
| --- | --- |
| Faculty already teaching | The faculty already has a lecture in the target slot. |
| Division busy | The division already has a lecture in the target slot. |
| Batch busy | The batch has a lab or tutorial in the target slot. |
| Shared-course students busy | Students of a minor or elective from other divisions are busy. |
| Room occupied | The target room already has a session. |
| Room unfit | Wrong room type, or seats below class strength. |
| Two pending tickets on the same slot or room | See batch 2. |
| Outside the calendar | Holiday, exam week, Sunday, or outside 08:00 to 20:00. |

A lab occupies two slots, so every check covers both hours.

### 6.2 No overrides

**Nobody can override a conflict**, including the HOD and Super Admin. The academic calendar is fixed, so an extra lecture cannot be scheduled on a holiday, in an exam week or on a Sunday.

### 6.3 Plain information, not conflicts

These were warnings in the spec. They are now **plain information**: shown on the form and on the ticket, never blocking, and requiring no acknowledgement or action.

- The room has no projector the session needs.
- The faculty is above the daily load cap or has too many lectures in a row.
- The change is at short notice.

## 7. What changes in the module spec

| Spec section | Change |
| --- | --- |
| 6 Roles | CR covers theory only; BR covers labs and tutorials; multiple CRs per division; no assign powers (answers Q1). |
| 7 and 9 | Consent for exchange and substitute is optional; affected faculty can revert. |
| 9 Approval model | Remove the reviewer timeout escalation. Ticket expires when the lecture starts. |
| 9 Open question Q2 | Answered: no forced review. |
| 9 Rejection | CR/BR can reject within scope; author can escalate to the HOD. |
| 10 Conflict rules | All checks block. Remove warnings and the "Default" column. Faculty overload, no projector and short notice become plain information. |
| 13 Q6 | Partly answered: no review timeout. "Short notice" no longer drives behaviour. |

## 8. Questions that were open here (answered in batch 2)

1. **Revert right:** can the other faculty revert at any time before the lecture, and is the revert itself conflict-checked if the original slot was taken?
2. **Two tickets on the same slot or room:** does the first to be approved win, or the first to be submitted?
3. **Extra lecture requested by a CR or BR:** must the faculty accept before it applies?
4. **Cancel:** can the faculty cancel directly with a reason, or does it need the HOD or a CR/BR acknowledgement?

Also still open from the module spec: the ERP API (Q3), the meaning of room "--" (Q4), whether extra lectures count toward faculty load (Q5).
