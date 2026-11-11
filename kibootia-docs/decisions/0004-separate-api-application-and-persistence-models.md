# 0004 Separate API, application and persistence models

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

Exposing JPA entities through APIs and letting application logic depend on them couples the API, business logic and the database schema. Source: project definition, sections 5.5 and 7.3.

## Decision

API DTOs, application/domain models and persistence entities are separate types. Entities are not exposed through public APIs, and application logic does not depend on concrete entity classes where a persistence boundary is warranted. The mapping overhead is accepted.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Entities as API responses | Couples the external contract to the schema |
| One model for all layers | Same coupling inside the application |

## Consequences

More types and mappers per feature ([ADR 0005](0005-hand-written-model-mappers.md)). Safer changes to schema and API independently.

## Status

accepted

## Related

[Model separation](../architecture/data/model-separation.md).
