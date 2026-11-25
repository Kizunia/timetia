# Roles and powers

- **Type:** rules
- **Module:** lecture-rescheduling
- **Status:** review
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

What each role can do in the lecture-rescheduling module. Original design: [module spec](../_archive/original-spec.md), section 6. Where this file differs from the spec, this file wins.

| Role | Powers |
| --- | --- |
| Super Admin | Configures rooms, slot grid, calendar and notification settings. Not part of routine approval. |
| Super HOD | Everything an HOD can, across departments. |
| HOD | Sees the department. Approves and rejects tickets. Handles escalated rejections. Raises tickets for an absent faculty. Approves permanent changes. Can revert any change in the department before the lecture starts. |
| Faculty | Raises reschedule, exchange, extra, cancel and substitute tickets for lectures they teach. Reviews tickets they are asked to review. Can revert an exchange or substitute applied to their lecture, including as its author. |
| CR | Reviews, acknowledges and may reject tickets for **theory** lectures of the division. Opens [issues](issues-from-cr-and-br.md) for theory lectures. |
| BR | The same powers as a CR, for **lab and tutorial** lectures of their batch. |
| Student | Read-only timetable and notifications. Cannot open issues. |

## CR and BR

- **CR and BR have no assign powers.** Assigning faculty, HODs and timetables stays outside this module's rules. In version 1 the base timetable is arranged manually; later it may come from an ERP.
- A class can have **multiple CRs** instead of "CR plus ACR". This is the current decision and the CR/ACR split may be revisited.
- Labs and tutorials belong to the BR because they are batch sessions. PAS is a regular theory lecture, so it is covered by the CR.
