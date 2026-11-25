# 0022 Maintain Kibootia's own documentation standards

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (documentation convention).

## Context

[ADR 0001](0001-adopt-structure-conventions-selectively.md) decided to rely on a separate `STRUCTURE.md` for documentation conventions and to leave that file unedited. The file was written for another project (a timetable product with ERP integration, academic roles and a lecture-rescheduling module). It still said "awaiting migration approval", described a migration of files that do not exist here, and defined rules (a mandatory `modules/` layout, per-module ADRs, user guides per academic persona) that Kibootia does not use. Readers had to combine the file with ADR 0001 to learn which rules applied, and the open question [OQ-15](open-questions.md) tracked the problem.

Kibootia is also a starter that is forked. Downstream projects need conventions that are about documentation, not about one product, and that keep working when the documentation folder is renamed.

## Decision

Kibootia maintains its own documentation standards in [documentation standards](../meta/documentation-standards.md). That document is the single authority for folder areas, naming, indexing, headers, status values, ADR rules, maintenance, relative links, portability and retirement of documents. `STRUCTURE.md` is deleted and is no longer a source of rules.

The conventions ADR 0001 adopted from `STRUCTURE.md` that still apply were carried into the new document; the product-specific material was not. The implementation-status vocabulary and source-of-truth order stay in [living documentation](../meta/living-documentation.md), which the standards link to.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep `STRUCTURE.md` and ADR 0001 as they are | Two documents are needed to find the current rules; stale "awaiting approval" content and another product's terms remain in the tree |
| Patch `STRUCTURE.md` in place | Keeps a file name and history that point to another project, and still needs ADR 0001 to say what applies |
| Move `STRUCTURE.md` to an archive folder | Preserves obsolete project-specific content that nothing needs; the history is in version control |
| Rewrite ADR 0001 to describe the new approach | Erases the record that the earlier decision was made; superseding is the ADR convention |

## Consequences

- One document defines documentation conventions. Maintainers do not need `STRUCTURE.md`.
- ADR 0001 stays as history. Its status is `deprecated`, superseded by this ADR. Its two recorded deviations (the `architecture/identity/` subfolder and the platform-wide open questions file) and the `Implementation:` line are now plain parts of the standards.
- Forks can keep, trim or supersede the standards through their own ADR, and can rename the documentation root without editing links ([portability](../meta/documentation-standards.md#10-portability-across-forks)).
- Open question OQ-15 (the stale `STRUCTURE.md`) is resolved by this ADR and has been removed from [open questions](open-questions.md).
- The deleted `STRUCTURE.md` remains visible in version control only. The ADR 0001 text is intentionally left as historical context; the file it names no longer exists.
- The glossary entry [feature](../shared/glossary/feature.md) and the documents referencing `STRUCTURE.md` were updated to point to the new standards.

## Migration of authority

| Before | After |
|---|---|
| Folder, naming, header, ADR and lifecycle rules in `STRUCTURE.md` | [Documentation standards](../meta/documentation-standards.md) |
| "Which parts of `STRUCTURE.md` apply" in ADR 0001 | Not needed; the standards contain only the applicable rules |
| Source-of-truth order, implementation-status vocabulary | Unchanged, in [living documentation](../meta/living-documentation.md) |
| OQ-15 | Resolved by this ADR |

## Status

accepted. Supersedes [ADR 0001](0001-adopt-structure-conventions-selectively.md).

## Related

[Documentation standards](../meta/documentation-standards.md), [living documentation](../meta/living-documentation.md), [ADR 0001](0001-adopt-structure-conventions-selectively.md).
