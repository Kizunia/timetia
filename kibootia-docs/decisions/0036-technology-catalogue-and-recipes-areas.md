# 0036 Technology catalogue and recipes as documentation areas

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (documentation convention).

## Context

Kibootia is a batteries-included foundation with defaults, optional modules and integrations that are only documented. The owner wants a catalogue of technologies and practical recipes. The documentation standards say a fact has one canonical home, and that [implementation status](../product/roadmap/implementation-status.md) is the single place for planned, observed or verified. [Documentation standards](../meta/documentation-standards.md#2-documentation-areas) also list the existing areas.

## Decision

Add two top-level documentation areas:

- **`technology/`** holds the catalogue in `README.md`: one row per technology with its tier ([ADR 0024](0024-package-modules-with-infrastructure-toggles.md)), selection status, version line and tested combinations, purpose and the ADR with the reasoning. It also lists technologies that were **considered and not selected**, each with the reason. A separate page exists only for a technology with real detail. The catalogue never repeats planned, observed or verified; it links to implementation status.
- **`recipes/`** holds task-oriented integration guides of a new document type, `recipe`, with a template. A recipe covers: the problem; when to use it and when not to; prerequisites and compatibility; steps; configuration and secrets; security; testing and verification; observability; removal or replacement. A recipe is marked as documented and not verified unless CI covers it.

The folder `recipes/` is created when the first real recipe exists. Until then the catalogue names planned recipes in plain text.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Inside `engineering/` | Many recipes are operational or security-related, which `engineering/` does not own |
| One combined document | Would pass the size guideline quickly |

## Consequences

The standards' area table and document type list change in the same change as this ADR.

## Status

accepted.

## Related

[Documentation standards](../meta/documentation-standards.md), [ADR 0022](0022-maintain-own-documentation-standards.md).
