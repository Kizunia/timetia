# 0005 Hand-written model mappers

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

[ADR 0004](0004-separate-api-application-and-persistence-models.md) requires conversion between model types. The project definition excludes "universal mapping frameworks". The owner chose hand-written mappers as the provisional answer.

## Decision

Provisional: convert between models with hand-written mapper classes or methods owned by the layer that needs the conversion. No mapping library.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| MapStruct | Adds a dependency and annotation processor; unclear fit with the mapping-framework exclusion |
| Left to each feature | Gives up the consistency the starter is meant to provide |

## Consequences

Explicit and easy to debug, but more boilerplate and one test per mapper. May be revisited with a superseding ADR if boilerplate becomes costly.

## Status

draft (provisional; listed in [open questions](open-questions.md))

## Related

[Model separation](../architecture/data/model-separation.md).
