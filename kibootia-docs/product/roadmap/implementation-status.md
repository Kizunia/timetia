# Implementation Status

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** this document is the reference for implementation status. The rows were first written from a snapshot of 2026-10-27 and updated on 2026-11-08 after milestone 0 was implemented.

The one place that compares what is intended with what exists. Values follow the [vocabulary](../../meta/living-documentation.md): planned, observed, verified. The [technology catalogue](../../technology/README.md) lists what is selected and links here for status.

**Milestone 0 is verified.** Its 13 tests and the secret scan passed in CI on Eclipse Temurin 25 on pull request #2 (run 42477195261, 2026-11-08) and again on `main` after the merge (run 42477240084), as the [verification standard](../../engineering/testing/verification-standard.md) requires. Milestone 1 is built and passes local runs (2026-11-09) but nothing from it has run in CI, so none of it is verified; milestones 2 to 6 are unbuilt. The container image scan (part of OQ-44) has not run.

## Existing capabilities

| Capability | Intended (decision) | Observed in repository | Level |
|---|---|---|---|
| Spring Boot backend application | [ADR 0002](../../decisions/0002-modular-monolith.md) | `backend/` with `BackendApplication` (Boot 4.1.1, Maven wrapper) | observed |
| Java baseline | [ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md) (supersedes ADR 0019) | `pom.xml` sets `java.version` 25; CI uses Temurin 25. Local build and tests passed on Oracle JDK 25.0.5 (2026-11-08) | verified (CI run 42477195261, 2026-11-08) |
| Package/module structure | [ADR 0002](../../decisions/0002-modular-monolith.md), [ADR 0003](../../decisions/0003-hexagonal-architecture-where-boundaries-matter.md), [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) | Six module packages under `com.kibootia.backend` as skeletons (`platform`, `identity`, `access`, `audit`, `files`, `notes`) | observed |
| ArchUnit rules and Spring Modulith verification | [ADR 0013](../../decisions/0013-archunit-architecture-enforcement.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) | Spring Modulith 2.1.2 and ArchUnit 1.5.1; `ModularityTest` and `ArchitectureTest` (rules 1, 4, 6, 8) pass locally; rules 5 and 7 not implemented | verified (CI run 42477195261, 2026-11-08) |
| Spring Security resource server | [ADR 0007](../../decisions/0007-spring-security-resource-server.md) | `platform/security`: stateless, deny-by-default chain, no CORS, 401 and 403 as Problem Details, a decoder that checks the public issuer and fetches keys from a separate address. `ResourceServerIT` (14 tests) passed locally on 2026-11-09 | observed (local run; no CI run yet) |
| Keycloak / OIDC | [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md), [ADR 0045](../../decisions/0045-keycloak-operations.md) | Keycloak 26.8.2 on PostgreSQL 17.11 in `compose.yaml`; realm `kibootia` applied by OpenTofu 1.13.9 with provider 5.10.0 (confidential PKCE client, API audience mapper, least-privilege admin service account, demo user). Sign-in through Next.js passed a Playwright test locally on 2026-11-09 | observed (local run; no CI run yet) |
| Audience validation, deny-by-default | [ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md) | `AudienceValidator` and the deny-by-default chain; tests for missing, wrong and absent audience and for unmapped paths | observed (local run; no CI run yet) |
| MySQL | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | MySQL 8.4 in `compose.yaml` (host port 3307); driver in pom; datasource properties set | observed |
| Spring Data JPA / Hibernate | [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md) | `spring-boot-starter-data-jpa` in the pom; `ddl-auto=validate` takes effect and validates the event publication table (passing locally) | verified (CI run 42477195261, 2026-11-08) |
| Flyway migrations | [ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md) | `db/migration/platform/V202611091200__platform_event_publication.sql`; migrate-from-empty is exercised by `BackendApplicationIT` | verified (CI run 42477195261, 2026-11-08) |
| Input validation | [API conventions](../../architecture/api/conventions.md) | Validation starter present; no usage | observed (dependency only) |
| Secret delivery from environment and files | [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md), [ADR 0016](../../decisions/0016-platform-injected-secrets.md) | Backend reads `MYSQL_PASSWORD` from the environment or the local `.env`; no secret in `application.properties` | observed |
| Externalised configuration | [configuration](../../operations/configuration/application-configuration.md) | No secret in version control; `.env.example` and `pnpm bootstrap` exist; a missing required setting stops startup with a named message (`RequiredSettingsTest`). The credentials that were committed to git history were rotated by the owner (reported 2026-11-08) | verified (CI run 42477195261, 2026-11-08); rotation reported by the owner |
| Health, logging, correlation ID | [observability baseline](../../operations/monitoring/observability-baseline.md) | Actuator with only `health` (liveness, readiness on MySQL) on port 8090, loopback only; ECS JSON console logs with trace and span IDs (tested); traces and metrics over OTLP. OTLP log export is not wired (OQ-42). No correlation ID is returned to callers except in Problem Details `traceId` | observed (local run; no CI run yet) |
| Testcontainers persistence tests | [ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md) | Testcontainers MySQL 8.4 with `@ServiceConnection`; `BackendApplicationIT` and `EventPublicationRegistryIT` pass locally | verified (CI run 42477195261, 2026-11-08) |
| CI | [ADR 0018](../../decisions/0018-ci-as-verification-standard.md) | `.github/workflows/ci.yml` (backend build and test, gitleaks secret scan) and `renovate.json` added; green on pull request #2 and on `main`. Renovate was installed by the owner on 2026-11-08 and has not yet opened a pull request. Milestone 1 added frontend, image build and scan, and end-to-end jobs; they have not run | verified for the backend and secret scan (CI run 42477195261, 2026-11-08); the rest written, not run |
| Next.js frontend | [ADR 0021](../../decisions/0021-frontend-beside-backend-in-one-repository.md), [ADR 0038](../../decisions/0038-frontend-stack.md), [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md) | `frontend/`: Next.js 16 BFF with sign-in, session, refresh, sign-out, the `/api/*` proxy, `/healthz`, a `/me` page; 83 Vitest tests, lint, type check and build passed locally on 2026-11-09. No generated API client, shadcn/ui or TanStack Query yet | observed (local run; no CI run yet) |
| Container image / deployment | [container deployment](../../operations/deployment/container-deployment.md), [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0047](../../decisions/0047-health-exposure-and-container-images.md) | `backend/Dockerfile` and `frontend/Dockerfile` (multi-stage, non-root); the `apps` Compose profile ran both with a working sign-in. No deployment recipe yet | observed (local run on 2026-11-09; no CI run yet) |
| Automated tests | [testing strategy](../../engineering/testing/strategy.md) | 13 tests (context and migration, event registry, Modulith, ArchUnit, required settings); all passed in one local run on 2026-11-08 | verified (CI run 42477195261, 2026-11-08) |

## Capabilities added by the decisions of 2026-10-28

The Level column was added on 2026-11-09.

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Next.js backend-for-frontend sign-in and encrypted cookie session | [ADR 0026](../../decisions/0026-nextjs-bff-session.md) | [1](milestone-1-walking-skeleton.md) | observed (local run on 2026-11-09; no CI run yet) |
| Local Keycloak with an imported realm, hardened | [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md) | [1](milestone-1-walking-skeleton.md) | observed (local run on 2026-11-09; no CI run yet); the realm is applied by OpenTofu, not imported |
| OTLP telemetry and the local Grafana LGTM container | [ADR 0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md) | [1](milestone-1-walking-skeleton.md) | partly observed (traces yes, log export no: OQ-42) |
| UUIDv7 identifiers, audit columns, optimistic locking | [ADR 0033](../../decisions/0033-uuidv7-identifiers.md) | [2](milestone-2-platform-conventions.md) | planned |
| Tenant context and isolation | [ADR 0028](../../decisions/0028-tenant-ready-schema.md) | [2](milestone-2-platform-conventions.md) | planned |
| Soft delete, restore and retention purge | [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md) | [2](milestone-2-platform-conventions.md) | planned |
| Outbox and lock-protected jobs | [ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md) | [2](milestone-2-platform-conventions.md) | planned |
| Generated OpenAPI description and client | [ADR 0038](../../decisions/0038-frontend-stack.md) | [2](milestone-2-platform-conventions.md) | planned |
| User profile, roles, permissions and decision point | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md) | [3](milestone-3-identity-and-access-administration.md) | planned |
| User and role administration UI, invitations, audit log viewer, rate limiter | [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md) | [3](milestone-3-identity-and-access-administration.md) | planned |
| Object storage module and avatar flow | [ADR 0029](../../decisions/0029-s3-object-storage-contract.md) | [4](milestone-4-files.md) | planned |
| Example feature and module-removal CI job | [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md) | [5](milestone-5-example-feature.md) | planned |
| Reference deployment, production guards, first recipes, release process | [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0023](../../decisions/0023-template-fork-adoption-model.md) | [6](milestone-6-production-readiness.md) | planned |
| Threat model | [threat model](../../operations/security/threat-model.md) | document written; kept current per milestone | planned |

## Capabilities added by the decisions of 2026-11-08

The Level column was added on 2026-11-09.

| Capability | Decision | Milestone | Level |
|---|---|---|---|
| Spring Modulith verification and the event publication registry as the outbox | [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) | [0](milestone-0-hygiene-and-guardrails.md), [2](milestone-2-platform-conventions.md) | verified (CI run 42477195261, 2026-11-08) |
| Setup scripts (`pnpm bootstrap`, `infra:up`, `dev`, `reset`) and the `apps` Compose profile | [ADR 0040](../../decisions/0040-local-development-topology-and-setup.md) | [0](milestone-0-hygiene-and-guardrails.md), [1](milestone-1-walking-skeleton.md) | observed (local run on 2026-11-09; no CI run yet) |
| Invitations, self-registration, default `user` role, email verification in the decision point | [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md) | [3](milestone-3-identity-and-access-administration.md) | planned |
| Generic `/api` proxy and CSRF rule in the Next.js server | [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md) | [1](milestone-1-walking-skeleton.md) | observed (local run on 2026-11-09; no CI run yet) |
| Problem Details errors, `/api/v1`, committed `openapi.json` with drift check | [ADR 0043](../../decisions/0043-http-api-conventions.md) | [2](milestone-2-platform-conventions.md) | planned (401 and 403 from the security filter chain already use Problem Details) |
| `scopeFor`, `@RequiresPermission`, `@NoAuthorization` and its ArchUnit rule | [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md) | [2](milestone-2-platform-conventions.md), [3](milestone-3-identity-and-access-administration.md) | planned |
| Keycloak on PostgreSQL, realm applied by OpenTofu, `auth.<domain>` exposure | [ADR 0045](../../decisions/0045-keycloak-operations.md) | [1](milestone-1-walking-skeleton.md), [6](milestone-6-production-readiness.md) | observed (local run on 2026-11-09; no CI run yet) (`auth.<domain>` exposure is milestone 6) |
| Audit module with user-ID-only entries and a 365-day default retention | [ADR 0046](../../decisions/0046-audit-log-design.md) | [2](milestone-2-platform-conventions.md), [3](milestone-3-identity-and-access-administration.md) | planned |
| Health on the management port, multi-stage Dockerfiles | [ADR 0047](../../decisions/0047-health-exposure-and-container-images.md) | [1](milestone-1-walking-skeleton.md) | observed (local run on 2026-11-09; no CI run yet) (the CI image scan has not run) |

## Deviations the owner has called out

- `spring-boot-admin-starter-server` was removed from `pom.xml` on 2026-11-08.
- Java 17 in the pom versus the decided Java 25: resolved on 2026-11-08 ([ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md)).
- Secrets handling meets the target convention in the repository, and the owner rotated the credentials that were committed to git history.
