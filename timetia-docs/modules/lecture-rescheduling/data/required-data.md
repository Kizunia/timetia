# Data the module needs

- **Type:** data
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Migrated from the original [spec](../_archive/original-spec.md), section 12.

In version 1 all of this is **seeded manually** ([ERP ownership](../integration/erp-ownership.md)). The "seen in the ERP export" column comes from the EduPlusCampus timetable exports for SY-CSSE-B and is kept as a guide to the shape of the data.

| Data | Details | Seen in the ERP export |
| --- | --- | --- |
| Calendar | Semester start and end, holidays, exam weeks, working days | Timetable per AY and semester, with W.E.F. date |
| Timetable versions | Which version is current, and its W.E.F. date | Version V1 |
| Slot grid | Slot length, breaks, per-department differences | Hourly slots 08:00 to 20:00, Mon to Fri |
| Divisions and batches | Batch membership and strength | SY-CSSE-B with batches B1 to B5 |
| Shared courses | Which batches take a shared course (see [shared courses](../rules/shared-courses.md)) | PAS minor (D2), FCTC (D38) |
| Subjects | Type, duration, required room type, weekly hours | Theory, Lab (2 h), Tutorial |
| Faculty | Department, leave, daily and weekly load caps | Load: Theory 14, Lab 8, Tutorial 2 |
| Rooms | See [room](../../../shared/entities/room.md) | B101, B105 to B108, B114; room "--" is read as online |
| Contacts | Email, phone, notification preferences | Not in the export |
