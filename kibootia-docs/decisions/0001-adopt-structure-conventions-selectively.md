# 0001 Adopt STRUCTURE.md conventions selectively

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (documentation convention).

## Context

[STRUCTURE.md](../STRUCTURE.md) was written for another project (a timetable product with ERP integration, academic roles and a lecture-rescheduling module). It contains useful general conventions and also content that does not apply to Kibootia. The owner stated this in the documentation interview.

## Decision

Use STRUCTURE.md for its conventions: top-level taxonomy, lowercase kebab-case names, no numeric folder prefixes, README index in every folder, one concern per file, the standard header, ADR rules with a central index, and the lifecycle and archiving rules. Do not apply the product-specific parts: ERP integration, the HOD/Faculty/CR/BR roles and entities, user guides per academic persona, the lecture-rescheduling module and its migration plan. Do not edit STRUCTURE.md. Create optional folders only when they contain real content. Do not create `docs/modules/` until Kibootia has a module to document.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Patch STRUCTURE.md for Kibootia | Owner preferred to leave the file untouched and reference it |
| Write a new structure document | Duplicates the conventions that apply |

## Consequences

- Two deviations were added for Kibootia and are recorded here: `architecture/identity/` is a subfolder of `architecture/`, and platform-wide open questions live in [open-questions.md](open-questions.md) because STRUCTURE.md only defines open questions per module.
- Each document carries an `Implementation:` line under the standard header ([living documentation](../meta/living-documentation.md)).
- STRUCTURE.md still says "awaiting migration approval" and describes a migration that does not apply. Readers should rely on this ADR for what is adopted.

## Status

accepted

## Related

[docs README](../README.md), [living documentation](../meta/living-documentation.md).
