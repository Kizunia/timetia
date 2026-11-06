# ERP ownership and version 1

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 5.

## Version 1: standalone

Decided 2026-11-06 ([decision 0001](../decisions/0001-standalone-version-1.md)). EduPlusCampus gives no API, so the module owns its base timetable, users, roles, rooms and calendar. All of it is **arranged and seeded manually**. There is no ERP sync and no ERP single sign-on to design for now. A webhook or similar integration is expected later.

## Intended split once an ERP is integrated

This is the original design, kept as the long-term target and not part of version 1.

| | Owned by the ERP | Owned by this module |
| --- | --- | --- |
| Users, roles, departments, divisions, batches | yes | read only |
| Subjects and faculty allotment | yes | read only |
| Base timetable (versions, W.E.F. dates) | yes | read only |
| Rooms | if present | extra attributes the ERP lacks |
| Academic calendar | if present | otherwise kept here |
| Tickets, reviews, conflicts | | yes |
| Change overlay | | yes |
| Notifications and audit log | | yes |

Rules for that future integration:

- Read ERP data through an API or webhooks, never by querying ERP tables directly.
- Never write into ERP tables. Publish events (`occurrence.moved`, `occurrence.cancelled`, `occurrence.added`, `occurrence.swapped`, `occurrence.substituted`) that other systems, such as attendance, may subscribe to. The module emits these events from version 1.
- Single sign-on against the ERP, with ERP roles mapped onto module roles.
- The module has its own database.
- If the ERP is unreachable, keep serving the last synced timetable plus the module's own changes.
- Reconcile permanent changes in the overlay with the ERP weekly timetable ([permanent changes](../rules/permanent-changes.md)).
