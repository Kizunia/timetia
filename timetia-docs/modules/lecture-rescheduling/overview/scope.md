# Scope

- **Type:** spec
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 4.

## In scope

- **Reschedule**: move one occurrence to another slot (and optionally another room).
- **Exchange**: two faculty swap occurrences.
- **Extra lecture**: add an occurrence that is not in the timetable.
- **Cancel**: drop an occurrence with no replacement.
- **Substitute**: another faculty takes an occurrence at the same time.
- **Issues** from CR and BR ([issues](../rules/issues-from-cr-and-br.md)).
- Conflict checking, review and approval, notifications, audit log.
- A live view of the timetable (base timetable plus approved changes) for every role.

## Out of scope

- Authoring tools for the base semester timetable. In version 1 the real timetable is **arranged and seeded manually** outside the module's workflows.
- Assigning HODs, faculty allotment, user and role management. In version 1 these are seeded manually too.
- Attendance marking. The module only emits events that an attendance module may use.
- Syncing with the ERP, for now.
