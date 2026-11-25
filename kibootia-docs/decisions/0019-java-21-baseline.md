# 0019 Java 21 baseline

- **Type:** adr
- **Module:** platform
- **Status:** deprecated
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. `backend/pom.xml` still sets `java.version` to 17.

> **Superseded by [ADR 0048](0048-java-25-baseline-on-temurin.md)** (Java 25 on Eclipse Temurin).

## Context

The project definition names no Java version. The initial build used 17. The owner chose to move to 21.

## Decision

The supported Java baseline is Java 21 (LTS).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep 17 | Older baseline; owner chose to move |
| Java 25 | Depends on tooling and image support not checked |

## Consequences

`pom.xml`, CI and container images must be updated to 21. Until then the docs and the build disagree; this is tracked in [implementation status](../product/roadmap/implementation-status.md).

## Status

deprecated. Superseded by [ADR 0048](0048-java-25-baseline-on-temurin.md).

## Related

[Local setup](../engineering/getting-started/local-setup.md), [container deployment](../operations/deployment/container-deployment.md).
