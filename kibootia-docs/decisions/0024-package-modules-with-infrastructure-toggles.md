# 0024 Package modules with infrastructure toggles

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. The repository has one package and no modules.

## Context

Kibootia is a batteries-included foundation (see [goals and non-goals](../product/goals-and-non-goals.md)), but "included" must not mean "mandatory". With the template model of [ADR 0023](0023-template-fork-adoption-model.md), every capability can in principle be deleted. A concrete mechanism is needed so that optional capabilities can be switched off or removed without breaking the build, the migrations or the frontend.

## Decision

The backend stays **one Maven project** ([ADR 0002](0002-modular-monolith.md)). Each module is a package whose boundaries are checked by ArchUnit ([ADR 0013](0013-archunit-architecture-enforcement.md)).

Modules that depend on infrastructure (object storage, observability, mail, and any later policy engine or broker) additionally get:

- one on/off configuration property;
- their own Flyway migration location;
- their own Docker Compose profile.

Every removable module has a documented removal recipe. CI proves that the application builds and starts with each optional module disabled. On/off properties exist only for infrastructure-backed modules, to keep the test matrix small. The frontend mirrors the layout with one feature folder per module.

Capabilities are classified into tiers: **core** (always present), **default-on module** (shipped and running, cleanly removable), **optional module** (shipped, off until enabled), **recipe** (documentation only) and **future**. The classification lives in the [technology catalogue](../technology/README.md).

The deletable example feature (working name "Notes") is a default-on module that forks are expected to remove.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Maven multi-module, one module per capability | Compiler-enforced boundaries, but a heavier build for every application and against the spirit of the "no Maven module per feature" non-goal |
| Package modules with no toggles | No "shipped but off" tier; every optional capability would become a recipe |

## Consequences

- Boundaries are enforced by tests, not by the compiler.
- Each toggle adds a configuration to test, so toggles are limited.
- Spring Modulith may help with module verification; its compatibility with the Spring Boot version in use is unchecked and it is not decided here.

## Status

accepted. Resolves part of OQ-21; the package names are still open.

## Related

[Layers and boundaries](../architecture/layers-and-boundaries.md), [architecture enforcement](../architecture/architecture-enforcement.md), [package structure](../engineering/conventions/package-structure.md).
