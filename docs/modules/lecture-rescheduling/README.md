# Lecture rescheduling

- **Type:** reference
- **Module:** lecture-rescheduling
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

A loosely coupled module for the college ERP that makes rescheduling, exchanging, substituting, cancelling and adding extra lectures quick and traceable. Faculty raise pull-request-style tickets, conflicts block like merge conflicts, and everyone concerned is notified.

**Precedence:** the original [module spec](../../lecture-rescheduling.md) is still the base design, but where a file in this folder differs from it, the file in this folder wins. The spec will be split into `overview/` and `requirements/` when it is migrated ([STRUCTURE.md](../../STRUCTURE.md), section 13).

| Part | Contents |
| --- | --- |
| [rules/](rules/README.md) | Approval, permissions, conflicts, reverts, cancellation, issues, rooms, load and more |
| [notifications/](notifications/README.md) | Channel, recipients and events |
| [integration/](integration/README.md) | Behaviour when the ERP changes |
| [open-questions.md](open-questions.md) | Unresolved decisions |
| [changelog.md](changelog.md) | History of decisions |

Shared terms such as [occurrence](../../../shared/glossary/occurrence.md) live in [shared/glossary](../../shared/glossary/README.md).
