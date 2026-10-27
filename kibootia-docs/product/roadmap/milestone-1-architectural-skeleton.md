# Milestone 1: Architectural Skeleton

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed (bare Spring Boot application only); nothing in the completion criteria is verified. See [implementation status](implementation-status.md).

## Scope (owner decision)

Establish the architectural skeleton: package structure, module contracts and enforceable dependency rules for a [modular monolith](../../decisions/0002-modular-monolith.md) with [hexagonal boundaries](../../decisions/0003-hexagonal-architecture-where-boundaries-matter.md).

## Completion criteria (proposed, not yet approved)

The definition requires measurable criteria but does not list them. These are the author's proposal:

- The package layout is decided and documented in [package structure](../../engineering/conventions/package-structure.md).
- [ArchUnit](../../decisions/0013-archunit-architecture-enforcement.md) tests exist for the rules in [architecture enforcement](../../architecture/architecture-enforcement.md) and pass in CI ([ADR 0018](../../decisions/0018-ci-as-verification-standard.md)).
- The Java baseline in `pom.xml` matches [ADR 0019](../../decisions/0019-java-21-baseline.md).
- A CI pipeline builds and tests the backend.

## Dependencies and open points

Boundary rules can only be tested across two or more feature packages. The decision not to ship a module yet is in [open questions](../../decisions/open-questions.md) (package layout).
