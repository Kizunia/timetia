# 0005 Hand-written model mappers

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Context

[ADR 0004](0004-separate-api-application-and-persistence-models.md) requires conversion between model types. The project definition excludes "universal mapping frameworks". The owner chose hand-written mappers, first provisionally and then, on 2026-11-08, as the accepted answer.

## Decision

Convert between models with hand-written mapper classes or methods owned by the layer that needs the conversion. No mapping library.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| MapStruct | Adds a dependency and annotation processor; unclear fit with the mapping-framework exclusion |
| Left to each feature | Gives up the consistency the starter is meant to provide |

## Consequences

Explicit and easy to debug, but more boilerplate and one test per mapper. May be revisited with a superseding ADR if boilerplate becomes costly.

## Status

accepted. Confirmed by the owner on 2026-11-08; resolves OQ-01.

## Related

[Model separation](../architecture/data/model-separation.md).
