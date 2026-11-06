# Change types

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Every change works on a single dated [occurrence](../glossary/occurrence.md) by default. A **permanent** change repeats every week from a date onward, always needs the HOD, and is applied by the module as a recurring change in its overlay ([permanent changes](../../modules/lecture-rescheduling/rules/permanent-changes.md)).

| Type | Input | Notes |
| --- | --- | --- |
| Reschedule | Source occurrence, target date and slot, optional room, optional description | A lab moves as a 2-slot block. Moving one batch never moves other batches in the same slot. |
| Exchange | Source occurrence, the other faculty's occurrence | Asking the other faculty for consent is optional. Both moves succeed or fail together. A 2-hour lab cannot be swapped with a 1-hour lecture unless both sides fit. |
| Extra lecture | Division or batch, subject, date and slot(s), room | The slot must be free for the whole division or batch. |
| Cancel | Source occurrence, mandatory reason | No replacement. Students are notified. |
| Substitute | Source occurrence, substitute faculty | Same time and room. Asking the substitute for consent is optional. |

See [approval model](../../modules/lecture-rescheduling/rules/approval-model.md) for consent and [cancellation](../../modules/lecture-rescheduling/rules/cancellation.md).
