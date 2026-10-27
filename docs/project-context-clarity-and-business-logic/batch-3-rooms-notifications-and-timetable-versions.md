# Batch 3: Rooms, Notifications and Timetable Versions

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

Third batch of business-logic decisions for the Lecture Rescheduling module. It builds on [Batch 1](batch-1-approval-roles-and-conflict-rules.md) and [Batch 2](batch-2-reverts-same-slot-tickets-cancellation-and-requests.md). Where it differs from the module spec, **this document wins**.

## 1. Rooms

- The **author picks the room**. The system only **suggests** free rooms that fit (room type, seats, projector).
- Every lecture has a **mode**: **in person** or **online**.
- An **in-person** lecture needs a room, so every changed in-person lecture has a place. This applies even when the original lecture had no room.
- An **online** lecture has **no room**, and the room checks are skipped for it. FCTC is an example: it runs fully online.
- Every lecture has one **location** field. For an in-person lecture it holds the **room number**; for an online lecture it holds the **meeting link** (mostly a Google Meet link) instead.
- The ERP's room "--" is read as **online**.
- For in-person lectures, the room conflict checks (room occupied, room unfit) always run on the chosen room and **block** as described in Batch 1.
- A missing projector remains plain information, never a conflict.

## 2. Notifications

- The first version uses the **in-app channel only**. Email, push, SMS and WhatsApp are not part of this version.
- Notified as in the module spec, section 11: the author, requested reviewers, mentioned faculty, the HOD where relevant, the CR/BR within scope, and every affected student, including students of other divisions in shared courses.
- Notification events: ticket raised, review requested, approved, rejected, expired, applied, reverted, and an issue mentions you (Batch 2).
- There is **no reminder** before a moved or extra lecture. Only the change notification is sent.

## 3. New ERP timetable version

The ERP can publish a new timetable version (new W.E.F. date) while changes are pending or already applied.

- Changes are **kept** and **re-checked** against the new base timetable.
- Any change that now conflicts is **flagged to its author and the HOD** so it can be fixed. Nothing is discarded silently.
- A flagged ticket follows the normal rule: it cannot be accepted until the conflict is resolved (Batch 1, section 6).

## 4. Changes to the module spec

| Spec section | Change |
| --- | --- |
| 11 Channels | Only in-app for the first version. |
| 11 When | Remove the reminder before a moved occurrence. |
| 8.1 step 3 | The author chooses the room; suggestions only. Online lectures store a meeting link in the same location field. |
| 5 and 10 | A new ERP version re-checks pending and applied changes and flags conflicts instead of discarding them. |
| 13 Q4 | Answered: room "--" means online. Online lectures have no room; in-person lectures need one. |

## 5. Still open

Edge cases not yet covered, for the next batch: a faculty on leave with pending tickets, extra lectures and faculty load (Q5), shared-course students (how their lists reach the module), and permanent changes in detail.
