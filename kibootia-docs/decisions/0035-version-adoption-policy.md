# 0035 Version adoption policy

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No dependency update automation exists.

## Context

The owner wants stable, compatible versions and is wary of versions that are too old or too new.

## Decision

Kibootia adopts a new **minor or major version** of a stack component (Spring Boot, Next.js, Keycloak and similar) only after its **first patch release** exists. Patch releases are taken promptly, and security fixes with priority. Combinations that have been tested together are recorded in the [technology catalogue](../technology/README.md). Dependency updates arrive as grouped automated pull requests.

The Java version is not decided here. It stays a separate decision ([ADR 0019](0019-java-21-baseline.md) and the Java 17 setting in `pom.xml` disagree; see [open questions](open-questions.md)).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Latest release immediately | Most breakage risk |
| One minor version behind | Shorter security support windows |

## Consequences

Upgrades lag releases slightly. Each upgrade is verified by CI before it is merged.

## Status

accepted.

## Related

[Verification standard](../engineering/testing/verification-standard.md).
