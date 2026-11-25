# Faculty reschedules a lab

- **Type:** flow
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 8.2, and updated to the current [rules](../rules/README.md). Where they differ, the rules win.

Same as [rescheduling a theory lecture](faculty-reschedule-theory.md), with these differences:

- The occurrence belongs to one batch and spans two consecutive slots. The target must have both slots free.
- The target room must be a lab of the right kind. For example, the DBMS lab is not the OOP lab.
- Other batches sharing the original slot are not affected.
- The reviewer within scope is the **BR** of the batch, not the CR ([permissions](../rules/permissions.md)).
