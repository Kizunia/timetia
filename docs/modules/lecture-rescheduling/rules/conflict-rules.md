# Conflict rules

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

How tickets are checked. Original design: [module spec](../../lecture-rescheduling.md), section 10. Where this file differs from the spec, this file wins.

## Every conflict blocks

There is **no warning level**. A ticket cannot be accepted until every [conflict](../../../shared/glossary/conflict.md) is resolved, the same way a merge cannot complete with unresolved conflicts. The author fixes the plan (another slot, room or date) or withdraws. Conflicts are checked when the ticket is raised and again at approval.

| Check | Example |
| --- | --- |
| Faculty already teaching | The faculty already has a lecture in the target slot. |
| Division busy | The division already has a lecture in the target slot. |
| Batch busy | The batch has a lab or tutorial in the target slot. |
| Room occupied | The target room already has a session (in-person lectures only). |
| Room unfit | Wrong room type, or seats below class strength (in-person lectures only). |
| Two tickets for the same slot or room | See [same-slot tickets](same-slot-tickets.md). |
| Outside the calendar | Holiday, exam week, Sunday, or outside 08:00 to 20:00. |

A lab occupies two slots, so every check covers both hours.

The shared-course students check is skipped in the first version ([shared courses](shared-courses.md)).

## No overrides

**Nobody can override a conflict**, including the HOD and Super Admin. The academic calendar is fixed, so an extra lecture cannot be scheduled on a holiday, in an exam week or on a Sunday.

## Plain information, not conflicts

These are shown on the form and on the ticket. They never block and need no acknowledgement.

- The room has no projector the session needs.
- The faculty is above the daily load cap or has too many lectures in a row ([faculty load](faculty-load.md)).
- The change is at short notice.
