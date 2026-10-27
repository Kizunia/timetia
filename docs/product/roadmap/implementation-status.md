# Implementation Status

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** this document is the reference for implementation status. Snapshot taken by reading the repository on 2026-10-27; no build or test command was run.

The one place that compares what is intended with what exists. Values follow the [vocabulary](../../meta/living-documentation.md): planned, observed, verified.

**Nothing is verified.** The only test, `BackendApplicationTests.contextLoads`, was not executed for this snapshot, and there is no CI.

| Capability | Intended (decision) | Observed in repository | Level |
|---|---|---|---|
| Spring Boot backend application | [ADR 0002](../../decisions/0002-modular-monolith.md) | `backend/` with `BackendApplication` (Boot 4.1.1, Maven wrapper) | observed |
| Java 21 baseline | [ADR 0019](../../decisions/0019-java-21-baseline.md) | `pom.xml` sets `java.version` 17 | planned (pom disagrees) |
| Package/module structure | [ADR 0002](../../decisions/0002-modular-monolith.md), [ADR 0003](../../decisions/0003-hexagonal-architecture-where-boundaries-matter.md) | Single package `com.kibootia.backend`, no modules | planned |
| ArchUnit rules | [ADR 0013](../../decisions/0013-archunit-architecture-enforcement.md) | No ArchUnit dependency or tests | planned |
| Spring Security resource server | [ADR 0007](../../decisions/0007-spring-security-resource-server.md) | Starters present; no security configuration, no issuer property | observed (dependency only) |
| Keycloak / OIDC | [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md) | Not in `compose.yaml`; no realm file | planned |
| Audience validation, deny-by-default | [ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md) (draft) | None | planned |
| MySQL | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | MySQL 8.4 in `compose.yaml` (host port 3307); driver in pom; datasource properties set | observed |
| Spring Data JPA / Hibernate | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | No JPA starter in pom. `spring.jpa.hibernate.ddl-auto=validate` is set but cannot take effect without it (inference) | planned |
| Flyway migrations | [ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md) | Flyway starters present; no migration scripts | observed (dependency only) |
| Input validation | [API conventions](../../architecture/api/conventions.md) | Validation starter present; no usage | observed (dependency only) |
| Vault for production secrets | [ADR 0015](../../decisions/0015-vault-for-production-secrets.md), [ADR 0016](../../decisions/0016-platform-injected-secrets.md) (draft) | None | planned |
| Externalised configuration | [configuration](../../operations/configuration/application-configuration.md) | `compose.yaml` reads two passwords from a root `.env`; `application.properties` contains a hardcoded datasource password. See [secrets management](../../operations/security/secrets-management.md) | observed (does not meet the target) |
| Health, logging, correlation ID | [observability baseline](../../operations/monitoring/observability-baseline.md) | No actuator dependency. `spring-boot-admin-starter-server` is present, which is not a decided capability | planned |
| Testcontainers persistence tests | [ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md) | No Testcontainers dependency | planned |
| CI | [ADR 0018](../../decisions/0018-ci-as-verification-standard.md) | No CI configuration | planned |
| Next.js frontend | [ADR 0021](../../decisions/0021-frontend-beside-backend-in-one-repository.md) (draft) | No `frontend/` directory | planned |
| Container image / deployment | [container deployment](../../operations/deployment/container-deployment.md) (draft) | No Dockerfile | planned |
| Automated tests | [testing strategy](../../engineering/testing/strategy.md) | One `contextLoads` test; test starters for Flyway, security, OAuth2 resource server, validation, WebMVC | observed (not run) |

## Deviations the owner has called out

- `spring-boot-admin-starter-server` in `pom.xml` is unintended and flagged for removal ([open question](../../decisions/open-questions.md)).
- Java 17 in the pom versus the decided Java 21.
- Secrets handling does not yet meet the target convention.
