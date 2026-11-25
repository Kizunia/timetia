# 0013 ArchUnit architecture enforcement

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed. ArchUnit 1.5.1 is a test dependency and `ArchitectureTest` holds rules 1, 4, 6 and 8 (passing locally on 2026-11-08; 1, 4 and 6 are vacuous until module code exists). No CI run yet.

> **Extended by [ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md)**: Spring Modulith verifies module boundaries; ArchUnit keeps the layer rules inside modules. This decision still holds.

## Context

Architecture that exists only in documents erodes. Source: project definition, section 5.6.

## Decision

Important package and dependency rules are enforced by ArchUnit tests, in addition to code review.

## Alternatives considered

None recorded.

## Consequences

Package naming must support selectors for the rules ([package structure](../engineering/conventions/package-structure.md)). Rules about feature boundaries need at least two features to be demonstrable.

## Status

accepted. Extended by [ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md).

## Related

[Architecture enforcement](../architecture/architecture-enforcement.md).
