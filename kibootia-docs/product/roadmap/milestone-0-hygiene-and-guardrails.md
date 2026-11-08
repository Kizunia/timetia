# Milestone 0: Hygiene and Guardrails

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** implemented in the repository and passing a local run on 2026-11-08 (Java 25, 13 tests including Testcontainers MySQL). **Not verified:** no CI run exists yet, so by the [verification standard](../../engineering/testing/verification-standard.md) the milestone is not complete until the pipeline in `.github/workflows/ci.yml` has run green on a pull request. See [implementation status](implementation-status.md).

## Scope

Make the repository safe and checkable before features are added: clean up known deviations, settle the Java baseline, externalise configuration, create the module skeleton with enforced boundaries, and start CI. Decisions: [ADR 0023](../../decisions/0023-template-fork-adoption-model.md), [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md).

The Java decision that blocked this milestone is made ([ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md)). The owner approved these criteria on 2026-11-08 (OQ-49). Rotating the credentials that were committed to git history is an owner task still open (OQ-13).

## Completion criteria (proposed; rewritten on 2026-11-08 after the second decision round, awaiting owner approval)

- `spring-boot-admin-starter-server` is removed; `adminer` is pinned to an exact version.
- Java 25 (Eclipse Temurin) is set in `pom.xml`, CI and local guidance, and the whole toolchain builds and tests on it. If it cannot, Java 21 is adopted through a new ADR (check OQ-44).
- Spring Modulith 2.1.x builds with the pinned Spring Boot 4.1.x, including the event publication registry on MySQL through a Flyway migration; otherwise the hand-written outbox fallback is chosen through a new ADR (check OQ-43).
- The module skeleton exists under `com.kibootia.backend` (`platform`, `identity`, `access`, `audit`, `files`, `notes`) as in [package structure](../../engineering/conventions/package-structure.md), with Spring Modulith verification and the ArchUnit rules of [architecture enforcement](../../architecture/architecture-enforcement.md) that can be meaningful at this stage.
- No secret value is in version control or in `application.properties`. Required settings are validated at startup with a clear message. `.env.example` exists and `pnpm setup` generates local secrets ([ADR 0040](../../decisions/0040-local-development-topology-and-setup.md)).
- A GitHub Actions pipeline builds and tests the backend, scans for secrets and runs Renovate ([verification standard](../../engineering/testing/verification-standard.md)).
- [Application configuration](../../operations/configuration/application-configuration.md) and [local setup](../../engineering/getting-started/local-setup.md) are accurate and work from a clean clone.
- Owner task, outside the repository: the credentials that were ever committed are rotated.
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).
