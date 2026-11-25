# Application module

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (definition).

A direct sub-package of the root package `com.kibootia.backend` that owns one area of behavior. Its top-level package is its public contract; its sub-packages are private. Spring Modulith verifies the boundaries. Decision: [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md). Not to be confused with a Maven module: the backend is one Maven project.
