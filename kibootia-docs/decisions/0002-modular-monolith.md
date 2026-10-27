# 0002 Modular monolith as initial architecture

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. The backend is a single Spring Boot application with no internal modules yet.

## Context

Kibootia needs clear internal boundaries without the operational cost of distributed services. Source: project definition, section 5.1.

## Decision

The backend is one deployable Spring Boot application whose features are logically independent modules with explicit public contracts. This is the initial choice and does not forbid later evolution.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Microservices | Network communication and operational complexity not justified; excluded for now |
| Unstructured monolith | No enforced boundaries; architecture decays |
| One Maven module per feature | Excluded as an initial requirement |

## Consequences

Boundaries must be enforced by tests ([ADR 0013](0013-archunit-architecture-enforcement.md)), since the compiler does not enforce them in a single Maven module. A concrete package layout is still needed ([package structure](../engineering/conventions/package-structure.md)).

## Status

accepted

## Related

[Modular monolith](../shared/glossary/modular-monolith.md), [layers and boundaries](../architecture/layers-and-boundaries.md).
