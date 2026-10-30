# Open Questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (list).

Live list of unresolved decisions, provisional choices, deviations observed in the repository and checks that must pass before a decision is final. Remove an entry (and note it in the relevant document or ADR) when it is resolved. All entries are owned by ishanKulkarni13 until reassigned.

Resolved on 2026-10-28 and removed from this list: OQ-02, OQ-03, OQ-04, OQ-06, OQ-20, OQ-22, OQ-23, OQ-25, OQ-27, OQ-28, OQ-29, OQ-31 (versions aside) and OQ-34. See the ADRs from [0023](0023-template-fork-adoption-model.md) to [0038](0038-frontend-stack.md).

## Provisional decisions awaiting confirmation

| ID | Question | Current provisional answer | Where |
|---|---|---|---|
| OQ-01 | Mapper approach | Hand-written mappers | [ADR 0005](0005-hand-written-model-mappers.md) |
| OQ-05 | Frontend location | `frontend/` beside `backend/`; consistent with [ADR 0023](0023-template-fork-adoption-model.md) but not yet confirmed on its own | [ADR 0021](0021-frontend-beside-backend-in-one-repository.md) |

## Deviations observed in the repository

| ID | Observation | Needed |
|---|---|---|
| OQ-10 | `spring-boot-admin-starter-server` in `pom.xml` is not in the project definition; owner says unintended | Remove it, or write an ADR if it becomes wanted (milestone 0) |
| OQ-11 | `pom.xml` sets Java 17; [ADR 0019](0019-java-21-baseline.md) decides 21. The owner deferred the Java decision; it blocks [milestone 0](../product/roadmap/milestone-0-hygiene-and-guardrails.md) | Decide separately, then update build, CI and images |
| OQ-12 | No JPA starter, although `ddl-auto=validate` is set | Add JPA in milestone 2 |
| OQ-13 | The root `.env` was tracked and is in git history; later commits stopped tracking it and ignore it. A hardcoded datasource password remains in `application.properties` | Rotate the credentials that were committed; see [secrets management](../operations/security/secrets-management.md) |
| OQ-14 | `adminer:latest` in `compose.yaml` is not version-pinned | Pin it, or remove Adminer (milestone 0) |

## Checks required before a decision is final

| ID | Check | Decision it gates |
|---|---|---|
| OQ-40 | RustFS: presigned PUT and GET and the other needed operations work on the pinned version; otherwise SeaweedFS becomes the local default | [ADR 0029](0029-s3-object-storage-contract.md) |
| OQ-41 | Hibernate in the chosen Spring Boot version: UUIDv7 generation, `BINARY(16)` mapping, soft-delete support | [ADR 0033](0033-uuidv7-identifiers.md), [ADR 0034](0034-soft-delete-with-retention-purge.md) |
| OQ-42 | OTLP log export wiring in the chosen Spring Boot version | [ADR 0030](0030-otlp-telemetry-and-local-lgtm.md) |
| OQ-43 | Spring Modulith: compatibility with the Spring Boot version in use, and whether it is wanted for module checks and the event registry | [ADR 0024](0024-package-modules-with-infrastructure-toggles.md), [ADR 0031](0031-transactional-outbox-and-locked-jobs.md) |

## Undecided

| ID | Question | Blocks |
|---|---|---|
| OQ-21 | Package names, location of ports, how a module declares its public contract (the mechanism is decided in [ADR 0024](0024-package-modules-with-infrastructure-toggles.md)) | [Milestone 0](../product/roadmap/milestone-0-hygiene-and-guardrails.md) |
| OQ-24 | Whether a default user role exists besides the built-in admin role | [Milestone 3](../product/roadmap/milestone-3-identity-and-access-administration.md) |
| OQ-26 | API error format (for example problem details), validation error shape, status mapping, versioning. Decided: offset paging with a maximum size, no CORS, 409 on edit conflicts | [Milestone 2](../product/roadmap/milestone-2-platform-conventions.md) |
| OQ-30 | Test naming and test data (branching, commits and CI are decided) | [Testing strategy](../engineering/testing/strategy.md) |
| OQ-32 | Container image build and base image, frontend deployment, health endpoint contract and exposure | [Milestone 0](../product/roadmap/milestone-0-hygiene-and-guardrails.md), [milestone 6](../product/roadmap/milestone-6-production-readiness.md) |
| OQ-33 | Naming scheme and catalog of Kibootia configuration properties and environment variables | [Application configuration](../operations/configuration/application-configuration.md) |
| OQ-35 | How the permission cache is invalidated when roles change | [Milestone 3](../product/roadmap/milestone-3-identity-and-access-administration.md) |
| OQ-36 | Production SMTP provider and mail configuration | [Milestone 6](../product/roadmap/milestone-6-production-readiness.md) |
| OQ-37 | Final name of the example feature (working name "Notes") | [Milestone 5](../product/roadmap/milestone-5-example-feature.md) |
