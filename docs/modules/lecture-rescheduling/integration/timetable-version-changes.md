# New ERP timetable version

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

The ERP can publish a new timetable version (new W.E.F. date) while changes are pending or already applied. Original design: [module spec](../../lecture-rescheduling.md), section 5.

- Changes are **kept** and **re-checked** against the new base timetable.
- Any change that now conflicts is **flagged to its author and the HOD** so it can be fixed. Nothing is discarded silently.
- A flagged ticket cannot be accepted until the conflict is resolved ([conflict rules](../rules/conflict-rules.md)).
