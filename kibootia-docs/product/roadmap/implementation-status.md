# Implementation Status

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** this document is the reference for implementation status. The observed rows come from a snapshot taken by reading the repository on 2026-10-27, rechecked for `.env` tracking, `.gitignore`, `application.properties` and the module list on 2026-10-28; no build or test command was run.

The one place that compares what is intended with what exists. Values follow the [vocabulary](../../meta/living-documentation.md): planned, observed, verified. The [technology catalogue](../../technology/README.md) lists what is selected and links here for status.

**Nothing is verified.** The only test, `BackendApplicationTests.contextLoads`, was not executed for this snapshot, and there is no CI.

## Existing capabilities

| Capability | Intended (decision) | Observed in repository | Level |
|---|---|---|---|
| Spring Boot backend application | [ADR 0002](../../decisions/0002-modular-monolith.md) | `backend/` with `BackendApplication` (Boot 4.1.1, Maven wrapper) | observed |
| Java baseline | [ADR 0019](../../decisions/0019-java-21-baseline.md); the owner deferred the Java decision | `pom.xml` sets `java.version` 17 | planned (pom disagrees) |
| Package/module structure | [ADR 0002](../../decisions/0002-modular-monolith.md), [ADR 0003](../../decisions/0003-hexagonal-architecture-where-boundaries-matter.md), [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md) | Single package `com.kibootia.backend`, no modules | planned |
| ArchUnit rules | [ADR 0013](../../decisions/0013-archunit-architecture-enforcement.md) | No ArchUnit dependency or tests | planned |
| Spring Security resource server | [ADR 0007](../../decisions/0007-spring-security-resource-server.md) | Starters present; no security configuration, no issuer property | observed (dependency only) |
| Keycloak / OIDC | [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md) | Not in `compose.yaml`; no realm file | planned |
| Audience validation, deny-by-default | [ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md) | None | planned |
| MySQL | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | MySQL 8.4 in `compose.yaml` (host port 3307); driver in pom; datasource properties set | observed |
| Spring Data JPA / Hibernate | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | No JPA starter in pom. `spring.jpa.hibernate.ddl-auto=validate` is set but cannot take effect without it (inference) | planned |
| Flyway migrations | [ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md) | Flyway starters present; no migration scripts | observed (dependency only) |
| Input validation | [API conventions](../../architecture/api/conventions.md) | Validation starter present; no usage | observed (dependency only) |
| Secret delivery from environment and files | [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md), [ADR 0016](../../decisions/0016-platform-injected-secrets.md) | None | planned |
| Externalised configuration | [configuration](../../operations/configuration/application-configuration.md) | `compose.yaml` reads two passwords from a root `.env`, which is no longer tracked and is in `.gitignore` but remains in git history; `application.properties` contains a hardcoded datasource password. See [secrets management](../../operations/security/secrets-management.md) | observed (does not meet the target) |
| Health, logging, correlation ID | [observability baseline](../../operations/monitoring/observability-baseline.md) | No actuator dependency. `spring-boot-admin-starter-server` is present, which is not a decided capability | planned |
| Testcontainers persistence tests | [ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md) | No Testcontainers dependency | planned |
| CI | [ADR 0018](../../decisions/0018-ci-as-verification-standard.md) | No CI configuration | planned |
| Next.js frontend | [ADR 0021](../../decisions/0021-frontend-beside-backend-in-one-repository.md) (draft), [ADR 0038](../../decisions/0038-frontend-stack.md) | No `frontend/` directory | planned |
| Container image / deployment | [container deployment](../../operations/deployment/container-deployment.md), [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md) | No Dockerfile | planned |
| Automated tests | [testing strategy](../../engineering/testing/strategy.md) | One `contextLoads` test; test starters for Flyway, security, OAuth2 resource server, validation, WebMVC | observed (not run) |

## Capabilities added by the decisions of 2026-10-28

All planned; none exists in the repository.

| Capability | Decision | Milestone |
|---|---|---|
| Next.js backend-for-frontend sign-in and encrypted cookie session | [ADR 0026](../../decisions/0026-nextjs-bff-session.md) | [1](milestone-1-walking-skeleton.md) |
| Local Keycloak with an imported realm, hardened | [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md) | [1](milestone-1-walking-skeleton.md) |
| OTLP telemetry and the local Grafana LGTM container | [ADR 0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md) | [1](milestone-1-walking-skeleton.md) |
| UUIDv7 identifiers, audit columns, optimistic locking | [ADR 0033](../../decisions/0033-uuidv7-identifiers.md) | [2](milestone-2-platform-conventions.md) |
| Tenant context and isolation | [ADR 0028](../../decisions/0028-tenant-ready-schema.md) | [2](milestone-2-platform-conventions.md) |
| Soft delete, restore and retention purge | [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md) | [2](milestone-2-platform-conventions.md) |
| Outbox and lock-protected jobs | [ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md) | [2](milestone-2-platform-conventions.md) |
| Generated OpenAPI description and client | [ADR 0038](../../decisions/0038-frontend-stack.md) | [2](milestone-2-platform-conventions.md) |
| User profile, roles, permissions and decision point | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md) | [3](milestone-3-identity-and-access-administration.md) |
| User and role administration UI, invitations, audit log viewer, rate limiter | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md) | [3](milestone-3-identity-and-access-administration.md) |
| Object storage module and avatar flow | [ADR 0029](../../decisions/0029-s3-object-storage-contract.md) | [4](milestone-4-files.md) |
| Example feature and module-removal CI job | [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md) | [5](milestone-5-example-feature.md) |
| Reference deployment, production guards, first recipes, release process | [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0023](../../decisions/0023-template-fork-adoption-model.md) | [6](milestone-6-production-readiness.md) |
| Threat model | [threat model](../../operations/security/threat-model.md) | document written; kept current per milestone |

## Deviations the owner has called out

- `spring-boot-admin-starter-server` in `pom.xml` is unintended and flagged for removal ([open question](../../decisions/open-questions.md)).
- Java 17 in the pom versus the decided Java 21; the owner deferred the Java decision.
- Secrets handling does not yet meet the target convention.
