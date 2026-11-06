# Lecture rescheduling

- **Type:** reference
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

A loosely coupled module that makes rescheduling, exchanging, substituting, cancelling and adding extra lectures quick and traceable. In version 1 it runs **standalone**, with a manually arranged base timetable and no ERP sync ([decision 0001](decisions/0001-standalone-version-1.md)). Faculty raise pull-request-style tickets, conflicts block like merge conflicts, and everyone concerned is notified.

**Precedence:** the original spec is archived at [_archive/original-spec.md](_archive/original-spec.md) and was split into the files below on 2026-11-06. If the archived spec differs from a file here, the file here wins.

| Part | Contents |
| --- | --- |
| [overview/](overview/README.md) | Problem, goals and scope |
| [requirements/](requirements/README.md) | One file per requirement |
| [flows/](flows/README.md) | One file per user flow |
| [rules/](rules/README.md) | Ticket lifecycle, approval, permissions, conflicts, reverts, cancellation, issues, rooms, load and more |
| [data/](data/README.md) | Data the module needs |
| [notifications/](notifications/README.md) | Channel, recipients and events |
| [integration/](integration/README.md) | Standalone in version 1, new timetable versions |
| [decisions/](decisions/README.md) | The module's decision records |
| [open-questions.md](open-questions.md) | Unresolved decisions |
| [changelog.md](changelog.md) | History of decisions |

Shared terms such as [occurrence](../../shared/glossary/occurrence.md) live in [shared/glossary](../../shared/glossary/README.md).
