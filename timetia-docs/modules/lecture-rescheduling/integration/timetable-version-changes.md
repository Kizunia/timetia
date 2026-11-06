# New timetable version

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

A new base timetable version (new W.E.F. date) can be loaded while changes are pending or already applied. Original design: [module spec](../../lecture-rescheduling.md), section 5.

- Changes are **kept** and **re-checked** against the new base timetable.
- Any change that now conflicts is **flagged to its author and the HOD** so it can be fixed. Nothing is discarded silently.
- A flagged ticket cannot be accepted until the conflict is resolved ([conflict rules](../rules/conflict-rules.md)).

## Version 1 is standalone

Decided 2026-11-06: in version 1 the module owns its base timetable, and the real timetable is **arranged and seeded manually**. A new version means loading a new manual seed, with the rules above. A webhook or similar ERP integration may come later, and ERP sync is out of scope for now.
