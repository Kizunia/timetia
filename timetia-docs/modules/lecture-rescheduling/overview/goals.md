# Goals

- **Type:** spec
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 2.

Make **rescheduling, exchanging and adding extra lectures** quick and traceable, from request to the moment every affected person knows about it.

The module is **loosely coupled and independent**: it can be deployed, upgraded or switched off without changing other systems.

**Version 1 is standalone.** It owns its base timetable, which is arranged and seeded manually, and has no ERP sync. A webhook or similar integration is expected later ([ERP ownership](../integration/erp-ownership.md), [decision 0001](../decisions/0001-standalone-version-1.md)).
