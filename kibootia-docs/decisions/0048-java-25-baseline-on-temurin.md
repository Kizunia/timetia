# 0048 Java 25 baseline on Eclipse Temurin

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. `backend/pom.xml` still sets `java.version` to 17.

## Context

[ADR 0019](0019-java-21-baseline.md) chose Java 21, `pom.xml` sets 17, and the owner deferred the decision (OQ-11), which blocked milestone 0 and the container base image. The owner brought it back into scope on 2026-11-08, wanting a stable, compatible version that is neither too old nor too new, consistent with the [version policy](0035-version-adoption-policy.md).

Research on 2026-11-08:

- Spring Boot 4.1 supports Java 17 to 26.
- Eclipse Temurin publishes free updates for Java 17 until at least October 2027, Java 21 until at least December 2029 and Java 25 until at least September 2031 (Adoptium support page).
- Java 25 is a long-term-support release from September 2025 and has had more than a year of patch releases.
- From Java 24, JEP 491 lets virtual threads synchronize without pinning their carrier thread, removing a known pitfall of using virtual threads with libraries that still use `synchronized`.

## Decision

The Java baseline is **Java 25 (LTS)**, using **Eclipse Temurin** for local development guidance and as the container base image (for example `eclipse-temurin:25-jre`).

Milestone 0 verifies that the whole toolchain builds and tests on Java 25: Spring Boot, Spring Modulith, ArchUnit, Testcontainers, the Maven plugins and the scanned image. If something blocks and cannot be fixed by a version update, the fallback is Java 21, recorded by a new ADR.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Java 21 (ADR 0019) | Widest tool compatibility, but free updates end about two years earlier and virtual threads pin on `synchronized` |
| Java 17 (current `pom.xml`) | Free updates end around October 2027, within Kibootia's first years |
| Java 26 or 27 | Not long-term-support releases |
| Any certified OpenJDK build | A named distribution keeps the documentation and base image concrete |

## Consequences

`pom.xml`, CI and the Dockerfile use 25. Developers install a Java 25 JDK. Forks inherit the longest available support window.

## Status

accepted. Supersedes [ADR 0019](0019-java-21-baseline.md). Resolves OQ-11.

## Related

[Local setup](../engineering/getting-started/local-setup.md), [container deployment](../operations/deployment/container-deployment.md), [technology catalogue](../technology/README.md).
