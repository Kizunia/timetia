# Open Questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (list).

Live list of unresolved decisions, deviations observed in the repository and checks that must pass before a decision is final. Remove an entry (and note it in the relevant document or ADR) when it is resolved. All entries are owned by ishanKulkarni13 until reassigned.

Resolved on 2026-10-28 and removed: OQ-02, OQ-03, OQ-04, OQ-06, OQ-20, OQ-22, OQ-23, OQ-25, OQ-27, OQ-28, OQ-29, OQ-31 (versions aside) and OQ-34; see ADRs [0023](0023-template-fork-adoption-model.md) to [0038](0038-frontend-stack.md).

Resolved on 2026-11-11 and removed: OQ-37 (the owner kept the name "Notes" for the example feature; [0052](0052-milestone-5-implementation-decisions.md)).

Resolved on 2026-11-10 and removed: OQ-40 (RustFS 1.0.4 passed 12 contract tests, including presigned PUT and GET, in a local run and in CI run 42873216224; [0051](0051-milestone-4-implementation-decisions.md)).

Resolved on 2026-11-09 and removed: OQ-41 (Hibernate 7.4 generates UUIDv7, maps `BINARY(16)` and filters soft-deleted rows with no fallback; [0049](0049-milestone-2-implementation-decisions.md)) and the deviations OQ-12, OQ-15 and OQ-16 (JPA added, Java 25, Adminer pinned).

Resolved on 2026-11-08 and removed: OQ-13 (the owner rotated the committed credentials), OQ-43 (Spring Modulith 2.1.2 with the event publication registry on MySQL passed in CI, run 42477195261), OQ-10 (the dependency was removed from `pom.xml`), OQ-49 (the owner approved the milestone completion criteria), OQ-01 ([0005](0005-hand-written-model-mappers.md)), OQ-05 ([0021](0021-frontend-beside-backend-in-one-repository.md)), OQ-11 ([0048](0048-java-25-baseline-on-temurin.md)), OQ-14 ([0040](0040-local-development-topology-and-setup.md)), OQ-21 ([0039](0039-spring-modulith-module-boundaries-and-outbox.md)), OQ-24 ([0041](0041-user-lifecycle-and-account-model.md)), OQ-26 ([0043](0043-http-api-conventions.md)), OQ-30 ([testing strategy](../engineering/testing/strategy.md)), OQ-32 ([0047](0047-health-exposure-and-container-images.md)), OQ-33 ([application configuration](../operations/configuration/application-configuration.md)), OQ-35 ([0044](0044-authorization-enforcement-conventions.md)) and the decision part of OQ-43 ([0039](0039-spring-modulith-module-boundaries-and-outbox.md)). The working record is the [decision log](../temp/kibootia-decision-log.md).

## Checks required before a decision is final

Each check runs in the milestone that first needs it. A failed check leads to the named fallback, recorded in a new ADR.

| ID | Check | Milestone | Fallback | Decision it gates |
|---|---|---|---|---|
| OQ-42 | OTLP log export wiring in the chosen Spring Boot version. Finding (2026-11-09, Boot 4.1.1): the `management.opentelemetry.logging.*` properties exist, but the exporter needs OpenTelemetry's Logback appender (`opentelemetry-logback-appender-1.0`, alpha-versioned), which `spring-boot-starter-opentelemetry` does not include. Not wired in milestone 1; Next.js logs also go to the console only. Traces and metrics are exported | 1 | Console JSON logs only until wired (in use) | [ADR 0030](0030-otlp-telemetry-and-local-lgtm.md) |
| OQ-44 | The whole toolchain builds and tests on Java 25 (Spring Boot, Spring Modulith, ArchUnit, Testcontainers, Maven plugins, scanned image). Build and tests pass on Eclipse Temurin 25 in CI (2026-11-08, run 42477195261); the image scan remains (the `images` CI job is written; the images build and ran locally on 2026-11-09) | 0 | Java 21 | [ADR 0048](0048-java-25-baseline-on-temurin.md) |
| OQ-45 | In the `apps` Compose profile, Keycloak's fixed public hostname with an internal back channel works, and Spring validates the public issuer while fetching keys internally. Passes locally (a real sign-in through both containers and a unit-level test of the split in `ResourceServerIT`, 2026-11-09); the CI end-to-end job passed on 2026-11-09 (run 42589726681) | 1 | Applications on the host only | [ADR 0040](0040-local-development-topology-and-setup.md) |
| OQ-46 | The OpenTofu Keycloak provider manages the pinned Keycloak version; Keycloak runs on the pinned PostgreSQL. Passes locally (Keycloak 26.8.2, provider 5.10.0, PostgreSQL 17.11: apply of 8 resources and an in-place change, 2026-11-09); passed in the CI end-to-end job (run 42589726681, 2026-11-09) | 1 | Pin earlier compatible versions | [ADR 0045](0045-keycloak-operations.md) |
| OQ-47 | OpenTofu's native state locking works on the production S3 service recommended in the reference deployment | 6 | Root-only state file on the server | [ADR 0045](0045-keycloak-operations.md) |

## Undecided

| ID | Question | Blocks |
|---|---|---|
| OQ-36 | Production SMTP settings for Keycloak (any provider; the backend sends no mail, [ADR 0041](0041-user-lifecycle-and-account-model.md)) | [Milestone 6](../product/roadmap/milestone-6-production-readiness.md) documentation only |
| OQ-48 | Kibootia's licence; `pom.xml` has an empty licence | The first release in [milestone 6](../product/roadmap/milestone-6-production-readiness.md); not coding |
