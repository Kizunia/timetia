# Shared courses

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

A shared course is a course whose students come from several divisions. **PAS** is a regular theory lecture of this kind, covered by the CR. **FCTC** runs fully online.

- **Shared courses are listed per batch.** Decided 2026-11-06. The shared-course lists are **seeded manually** and say which batches take each shared course, for example "PAS is taken by batches X, Y and Z". The list is per batch, not per division and not per student.
- **Conflict check:** for a shared course, the "batch busy" and "division busy" checks run for **every batch on the list**, so a shared lecture cannot move to a slot where one of those batches already has a lecture ([conflict rules](conflict-rules.md)). A conflict blocks like any other.
- **Notifications:** students of every listed batch are notified of changes to the shared lecture.
- This replaces the earlier decision to skip the shared-course check in version 1. The earlier reason was that the ERP's student lists were not available.
