# Open questions

- **Type:** reference
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

Live list of unresolved decisions. Items answered by the owner are in the resolved table below. Original questions: [module spec](../lecture-rescheduling.md), section 13.

| # | Question | Current position | Owner |
| --- | --- | --- | --- |
| 3 | Should shared-course student lists be read from the ERP later? | Check skipped in version 1 ([shared courses](rules/shared-courses.md)). | ishanKulkarni13 |
| 8 | Since the ERP gives no help (see resolved 1), is the module standalone with its own base timetable? | Not yet decided. Affects the "ERP owns the base timetable" statements in the spec. | ishanKulkarni13 |

## Resolved (2026-11-06)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Does EduPlusCampus expose an API or webhooks? | **No.** The ERP will not help in any way. Data is **seeded manually**. |
| 2 | Reconcile permanent changes in the overlay with the ERP weekly timetable | **No sync** between the module and the ERP for now ([permanent changes](rules/permanent-changes.md)). |
| 4 | Should the online meeting link become required? | **No**, it stays optional ([rooms](rules/rooms-and-location.md)). |
| 5 | Keep "multiple CRs per class", or return to CR/ACR? | **Keep multiple CRs** ([permissions](rules/permissions.md)). |
| 6 | Other notification channels and reminders | **In-app only, no reminders** in version 1 ([triggers](notifications/triggers.md)). |
| 7 | Can students see tickets themselves? | **Results only** for now ([ticket lifecycle](rules/ticket-lifecycle.md)). |
