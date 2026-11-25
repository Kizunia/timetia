# Open questions

- **Type:** reference
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-25

Live list of unresolved decisions. Items answered by the owner are in the resolved table below. Original questions: [module spec](_archive/original-spec.md), section 13.

| # | Question | Current position | Owner |
| --- | --- | --- | --- |
| - | No open questions right now. | - | - |

## Deferred features (not built; out of scope for the course project)

| Feature | Rule or requirement | What happens today |
| --- | --- | --- |
| Ticket history timeline | [audit log](requirements/audit-log.md) | Actions are in the administrator's audit log; there is no per-ticket view |
| Holidays, exam weeks, configurable slot grid | [conflict rules](rules/conflict-rules.md) | The grid is fixed at 08:00 to 20:00 and no day is a holiday |
| Shared courses | [shared courses](rules/shared-courses.md) | Not handled: checks and notifications cover the lecture's own division or batch |
| Room fit as a conflict | [rooms](rules/rooms-and-location.md) | Free rooms of the wrong kind are marked, not refused |
| Information notes (projector, load, short notice) | [faculty load](rules/faculty-load.md) | Not shown |
| `occurrence.*` events for attendance | [scope](overview/scope.md) | Not published |
| Re-checking tickets when a new timetable version is published | [timetable version changes](integration/timetable-version-changes.md) | Publishing is refused while applied changes would be lost |
| Approval of an HOD's own lecture by a Super HOD | [approval model](rules/approval-model.md) | Not required |
| Channels other than in-app, reminders | [triggers](notifications/triggers.md) | In-app only |

## Resolved (2026-11-06)

| # | Question | Decision |
| --- | --- | --- |
| 1 | Does EduPlusCampus expose an API or webhooks? | **No.** The ERP will not help in any way. Data is **seeded manually**. |
| 2 | Reconcile permanent changes in the overlay with the ERP weekly timetable | **No sync** between the module and the ERP for now ([permanent changes](rules/permanent-changes.md)). |
| 4 | Should the online meeting link become required? | **No**, it stays optional ([rooms](rules/rooms-and-location.md)). |
| 5 | Keep "multiple CRs per class", or return to CR/ACR? | **Keep multiple CRs** ([permissions](rules/permissions.md)). |
| 6 | Other notification channels and reminders | **In-app only, no reminders** in version 1 ([triggers](notifications/triggers.md)). |
| 7 | Can students see tickets themselves? | **Results only** for now ([ticket lifecycle](rules/ticket-lifecycle.md)). |
| 8 | Is the module standalone, with its own base timetable? | **Standalone for version 1.** The real timetable is arranged manually. A webhook or similar ERP integration may come later; ignore ERP sync for now. | Decided |
| 3 | Should shared-course student lists be read from the ERP? | **Per batch, seeded manually** in version 1 ([shared courses](rules/shared-courses.md)). ERP lists may come with a later integration. |
