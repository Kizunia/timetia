# Batch 2: Reverts, Same-Slot Tickets, Cancellation and Requests

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

Second batch of business-logic decisions for the Lecture Rescheduling module. It builds on [Batch 1](batch-1-approval-roles-and-conflict-rules.md) and uses the same terms. Where this batch differs from the module spec, **this document wins**.

## 1. Revert

An exchange or substitute can be applied without the other faculty's consent (Batch 1, section 4.2). The right to revert is what protects that faculty.

- The faculty affected by an exchange or substitute can **revert it at any time before the lecture starts**. Once the lecture starts, the occurrence is fixed.
- A revert restores the original occurrence.
- The revert is **conflict-checked like any other change**. If the original slot has been taken in the meantime, the revert is **blocked** until the conflict is resolved.
- A revert notifies everyone who was notified of the original change.
- Every revert is written to the audit log.

## 2. Two tickets for the same slot or room

Example: two faculty both ask for Friday 15:00 in room B101.

- The ticket that is **approved or applied first wins**.
- The other ticket becomes a **conflict** (room occupied or faculty/division busy), and its author must pick another slot or room.
- Submitting a ticket does **not** reserve the slot. A ticket in review holds nothing until it is approved.

This follows the merge-conflict idea: the first merge wins and the later one has to be reworked.

## 3. Cancel

- The faculty who teaches the occurrence can **cancel it directly**, with a **mandatory reason**.
- No HOD or CR/BR approval is needed.
- Students are notified, and the ERP attendance module is informed through the `occurrence.cancelled` event.
- A cancel is an ordinary ticket and appears in the audit log. The HOD can still cancel on behalf of an absent faculty (module spec, section 8.5).

## 4. Requests from CR and BR: issues

Requests from the student side work **exactly like GitHub issues**. This replaces the module spec rule that a CR-initiated extra lecture "needs the faculty's acceptance" (section 8.4).

### 4.1 How it works

1. A **CR** (theory) or **BR** (labs and tutorials) opens an **issue** describing what the class wants, for example "extra DBMS lecture before the test".
2. They can **mention or link faculty** in the issue.
3. Each mentioned faculty receives a notification that **an issue mentions them**.
4. The faculty reads it. If they agree, they raise the normal **extra lecture ticket** themselves. If not, they reply or ignore it.

### 4.2 Rules

| Rule | Outcome |
| --- | --- |
| Who can open an issue | **Only CR and BR**, each within their scope: CR for theory, BR for labs and tutorials. Ordinary students cannot. |
| Does an issue change the timetable | **No.** It is only a request. Only a ticket changes the timetable. |
| Acceptance step | None. A mentioned faculty is notified, nothing more. |
| Who gets notified | Every mentioned faculty. The HOD is notified only when mentioned. |
| Linking | An issue can be linked to the ticket that answers it, as with a GitHub issue linked to a pull request. |

### 4.3 Not decided yet

Whether issues can be closed, who can close them, and whether replies on an issue are allowed to everyone concerned or only to faculty and the author. These are listed in section 6.

## 5. Changes to the module spec

| Spec section | Change |
| --- | --- |
| 8.4 Extra lecture | A CR or BR no longer raises the request on a faculty's behalf. They open an issue, and the faculty raises the ticket. |
| 8.6 CR / BR | "May request an extra lecture" becomes "may open issues, and mention faculty in them". |
| 9 Reverted state | Revert is allowed until the lecture starts and is conflict-checked. |
| 10 Two pending tickets | First approved or applied wins. Submitting does not reserve. |
| 7 Cancel | Direct by the teaching faculty, with a mandatory reason. |

## 6. Still open

1. Can issues be closed, and by whom? Can they have replies and labels?
2. Do issues expire, for example when the date they asked about passes?
3. Which rooms and notification settings apply (batch 3: rooms, notifications and edge cases)?
