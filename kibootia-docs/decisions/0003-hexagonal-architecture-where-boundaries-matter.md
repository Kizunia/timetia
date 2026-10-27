# 0003 Hexagonal architecture where boundaries matter

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

Application logic should not be coupled to concrete infrastructure, but forcing an interface on every class adds noise. Source: project definition, section 5.2.

## Decision

Apply hexagonal architecture where it protects a meaningful boundary: application logic defines ports, infrastructure adapters implement them. Not every class needs an interface and not every feature needs an elaborate domain layer.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Plain layered architecture | Allows application logic to depend on infrastructure details |
| Hexagonal everywhere | Ceremony without benefit where no boundary exists |

## Consequences

Authors must judge where a boundary is worthwhile; the judgement is guided by [layers and boundaries](../architecture/layers-and-boundaries.md) and tested by [architecture rules](../architecture/architecture-enforcement.md).

## Status

accepted

## Related

[Hexagonal architecture](../shared/glossary/hexagonal-architecture.md), [port](../shared/glossary/port.md), [adapter](../shared/glossary/adapter.md).
