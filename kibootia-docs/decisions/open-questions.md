# Open Questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (list).

Live list of unresolved decisions, deviations observed in the repository and checks that must pass before a decision is final. Remove an entry (and note it in the relevant document or ADR) when it is resolved. All entries are owned by ishanKulkarni13 until reassigned.

Resolved on 2026-10-28 and removed: OQ-02, OQ-03, OQ-04, OQ-06, OQ-20, OQ-22, OQ-23, OQ-25, OQ-27, OQ-28, OQ-29, OQ-31 (versions aside) and OQ-34; see ADRs [0023](0023-template-fork-adoption-model.md) to [0038](0038-frontend-stack.md).

Resolved on 2026-11-08 and removed: OQ-01 ([0005](0005-hand-written-model-mappers.md)), OQ-05 ([0021](0021-frontend-beside-backend-in-one-repository.md)), OQ-11 ([0048](0048-java-25-baseline-on-temurin.md)), OQ-14 ([0040](0040-local-development-topology-and-setup.md)), OQ-21 ([0039](0039-spring-modulith-module-boundaries-and-outbox.md)), OQ-24 ([0041](0041-user-lifecycle-and-account-model.md)), OQ-26 ([0043](0043-http-api-conventions.md)), OQ-30 ([testing strategy](../engineering/testing/strategy.md)), OQ-32 ([0047](0047-health-exposure-and-container-images.md)), OQ-33 ([application configuration](../operations/configuration/application-configuration.md)), OQ-35 ([0044](0044-authorization-enforcement-conventions.md)) and the decision part of OQ-43 ([0039](0039-spring-modulith-module-boundaries-and-outbox.md)). The working record is the [decision log](../temp/kibootia-decision-log.md).

## Deviations observed in the repository

All are corrected in [milestone 0](../product/roadmap/milestone-0-hygiene-and-guardrails.md).

| ID | Observation | Needed |
|---|---|---|
| OQ-10 | `spring-boot-admin-starter-server` in `pom.xml` is not a decided capability; the owner says it is unintended | Remove it |
| OQ-12 | No JPA starter, although `ddl-auto=validate` is set | Add JPA (milestone 2) |
| OQ-13 | The root `.env` was tracked and is in git history; a hardcoded datasource password remains in `application.properties` | Owner rotates the committed credentials; remove the hardcoded value ([secrets management](../operations/security/secrets-management.md)) |
| OQ-15 | `pom.xml` sets `java.version` 17; the decision is Java 25 ([ADR 0048](0048-java-25-baseline-on-temurin.md)) | Update build, CI and images |
| OQ-16 | `compose.yaml` uses `adminer:latest` | Pin an exact version ([ADR 0040](0040-local-development-topology-and-setup.md)) |

## Checks required before a decision is final

Each check runs in the milestone that first needs it. A failed check leads to the named fallback, recorded in a new ADR.

| ID | Check | Milestone | Fallback | Decision it gates |
|---|---|---|---|---|
| OQ-40 | RustFS: presigned PUT and GET and the other needed operations on the pinned version | 4 | SeaweedFS as the local default | [ADR 0029](0029-s3-object-storage-contract.md) |
| OQ-41 | Hibernate in the chosen Spring Boot version: UUIDv7 generation, `BINARY(16)` mapping, soft-delete support | 2 | Application-side generation or explicit mapping | [ADR 0033](0033-uuidv7-identifiers.md), [ADR 0034](0034-soft-delete-with-retention-purge.md) |
| OQ-42 | OTLP log export wiring in the chosen Spring Boot version | 1 | Console JSON logs only until wired | [ADR 0030](0030-otlp-telemetry-and-local-lgtm.md) |
| OQ-43 | Spring Modulith 2.1.x builds with the pinned Spring Boot 4.1.x, including the event publication registry on MySQL through a Flyway migration | 0 | Spring Modulith checks plus a hand-written outbox | [ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md) |
| OQ-44 | The whole toolchain builds and tests on Java 25 (Spring Boot, Spring Modulith, ArchUnit, Testcontainers, Maven plugins, scanned image) | 0 | Java 21 | [ADR 0048](0048-java-25-baseline-on-temurin.md) |
| OQ-45 | In the `apps` Compose profile, Keycloak's fixed public hostname with an internal back channel works, and Spring validates the public issuer while fetching keys internally | 1 | Applications on the host only | [ADR 0040](0040-local-development-topology-and-setup.md) |
| OQ-46 | The OpenTofu Keycloak provider manages the pinned Keycloak version; Keycloak runs on the pinned PostgreSQL | 1 | Pin earlier compatible versions | [ADR 0045](0045-keycloak-operations.md) |
| OQ-47 | OpenTofu's native state locking works on the production S3 service recommended in the reference deployment | 6 | Root-only state file on the server | [ADR 0045](0045-keycloak-operations.md) |

## Undecided

| ID | Question | Blocks |
|---|---|---|
| OQ-36 | Production SMTP settings for Keycloak (any provider; the backend sends no mail, [ADR 0041](0041-user-lifecycle-and-account-model.md)) | [Milestone 6](../product/roadmap/milestone-6-production-readiness.md) documentation only |
| OQ-37 | Final name of the example feature (working name "Notes") | [Milestone 5](../product/roadmap/milestone-5-example-feature.md) |
| OQ-48 | Kibootia's licence; `pom.xml` has an empty licence | The first release in [milestone 6](../product/roadmap/milestone-6-production-readiness.md); not coding |
| OQ-49 | Approval of the milestone completion criteria rewritten on 2026-11-08 | The start of [milestone 0](../product/roadmap/milestone-0-hygiene-and-guardrails.md) |
