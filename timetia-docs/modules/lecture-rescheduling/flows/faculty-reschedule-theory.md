# Faculty reschedules a theory lecture

- **Type:** flow
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 8.1, and updated to the current [rules](../rules/README.md). Where they differ, the rules win.

1. Faculty opens the module and sees their personal timetable.
2. Faculty clicks the edit icon on an occurrence and chooses **Reschedule**. This fixes the *from* side.
3. The timetable opens again with free slots highlighted for that division. Faculty clicks the *to* slot. The module **suggests** free rooms that fit; the author picks the room ([rooms](../rules/rooms-and-location.md)).
4. Faculty may add a description.
5. The module runs the [conflict checks](../rules/conflict-rules.md). Every conflict blocks, and a ticket with a conflict cannot be applied. Plain information such as "no projector" is shown but does not block.
6. Faculty chooses how to proceed:
   - **Apply directly** ([self-approval](../../../shared/glossary/self-approval.md)), or
   - **Request review** from any combination of the HOD, the CR within scope, the affected faculty and other faculty ([approval model](../rules/approval-model.md)).
7. Once the ticket is applied, the change overlay is updated and everyone concerned is notified ([ticket lifecycle](../rules/ticket-lifecycle.md)).
