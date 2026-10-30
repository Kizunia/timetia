# Technology Catalogue

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (catalogue). Whether a technology is planned, observed or verified is **not** recorded here; see [implementation status](../product/roadmap/implementation-status.md).

One row per technology: its **tier**, whether it is **selected**, why, and where the reasoning lives ([ADR 0036](../decisions/0036-technology-catalogue-and-recipes-areas.md)). Versions follow the [version policy](../decisions/0035-version-adoption-policy.md): a new minor or major version is adopted only after its first patch release.

## Tiers

| Tier | Meaning |
|---|---|
| core | Always present |
| default-on | A module that ships and runs by default and can be removed or switched off with a tested recipe |
| optional | Ships, but off until enabled |
| recipe | Documentation only; no code or service ships |
| future | Possible later; not planned in detail |

Selection status: **selected** (chosen), **candidate** (leading option, choice not final) or **not selected** (considered and rejected, with the reason).

## Tested combinations

None recorded yet. A combination is recorded here only after a passing CI run covers it.

## Selected and candidate technologies

| Technology | Tier | Selection | Purpose | Decision |
|---|---|---|---|---|
| Spring Boot (Java) | core | selected; the Java version is [deferred](../decisions/open-questions.md) | Backend application | [0002](../decisions/0002-modular-monolith.md), [0019](../decisions/0019-java-21-baseline.md) |
| Maven | core | selected | Backend build | [project definition](../temp/kibootia-project-definition.md) |
| Next.js (App Router), TypeScript, pnpm | core | selected | Frontend and backend-for-frontend | [0038](../decisions/0038-frontend-stack.md), [0026](../decisions/0026-nextjs-bff-session.md) |
| Tailwind CSS, shadcn/ui | core | selected | Frontend styling and components | [0038](../decisions/0038-frontend-stack.md) |
| `openid-client` | core | selected | OpenID Connect in the Next.js server | [0026](../decisions/0026-nextjs-bff-session.md) |
| TanStack Query, React Hook Form, Zod | core | selected | Frontend data and forms | [0038](../decisions/0038-frontend-stack.md) |
| Generated OpenAPI client | core | selected; generator candidate: openapi-typescript with openapi-fetch | Typed API calls | [0038](../decisions/0038-frontend-stack.md) |
| Keycloak with OpenID Connect | core | selected | Identity and user lifecycle | [0006](../decisions/0006-keycloak-oidc-identity.md), [0025](../decisions/0025-identity-and-authorization-data-split.md), [0020](../decisions/0020-keycloak-in-local-compose.md) |
| Spring Security resource server | core | selected | Token validation and request protection | [0007](../decisions/0007-spring-security-resource-server.md), [0008](../decisions/0008-audience-validation-and-deny-by-default.md) |
| Central authorization decision point (Java policies) | core | selected | Permission, ownership and contextual checks | [0027](../decisions/0027-central-authorization-decision-point.md) |
| MySQL 8.4 | core | selected | Relational storage | [0011](../decisions/0011-mysql-jpa-hibernate-persistence.md) |
| Spring Data JPA, Hibernate | core | selected | Persistence | [0011](../decisions/0011-mysql-jpa-hibernate-persistence.md) |
| Flyway | core | selected | Schema migrations | [0012](../decisions/0012-flyway-managed-schema-evolution.md) |
| ArchUnit | core | selected | Architecture rules as tests | [0013](../decisions/0013-archunit-architecture-enforcement.md) |
| Testcontainers (MySQL) | core | selected | Persistence tests on real MySQL | [0017](../decisions/0017-testcontainers-mysql-for-persistence-tests.md) |
| Vitest, Testing Library, Playwright | core | selected | Frontend and end-to-end tests | [0038](../decisions/0038-frontend-stack.md) |
| GitHub Actions | core | selected | CI, the verification evidence | [0018](../decisions/0018-ci-as-verification-standard.md) |
| Renovate, gitleaks, Trivy | core | selected (tools) | Dependency updates, secret and image scanning | [verification standard](../engineering/testing/verification-standard.md) |
| Transactional outbox, lock-protected jobs | core | selected; library candidates: Spring Modulith event registry, a hand-written table, ShedLock | Durable events and single-run jobs | [0031](../decisions/0031-transactional-outbox-and-locked-jobs.md) |
| OpenTelemetry starter, Micrometer, Actuator | default-on | selected | Logs, metrics, traces over OTLP; health | [0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md) |
| Grafana LGTM container (local) | default-on | selected for local development only | Receives and shows telemetry | [0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md) |
| User and role administration with audit log | default-on | selected | Administration interface, runtime-editable roles | [0025](../decisions/0025-identity-and-authorization-data-split.md) |
| In-app rate limiter | default-on | selected; library not chosen | Throttle sensitive endpoints | [API conventions](../architecture/api/conventions.md) |
| Object storage module (S3 API) | default-on | selected | Avatars and uploads | [0029](../decisions/0029-s3-object-storage-contract.md) |
| RustFS (local S3 server) | default-on | candidate; a verification gate is open | Local object storage | [0029](../decisions/0029-s3-object-storage-contract.md) |
| Local mail catcher (for example Mailpit) | default-on | candidate | Receives invitation mail locally | [0025](../decisions/0025-identity-and-authorization-data-split.md) |
| Generated API documentation UI (library not chosen) | default-on | candidate | Interactive API documentation locally | [goals and non-goals](../product/goals-and-non-goals.md) |
| Example feature ("Notes") | default-on, meant to be removed | selected | Shows how to add a feature | [0024](../decisions/0024-package-modules-with-infrastructure-toggles.md) |
| Docker Compose with a TLS reverse proxy (for example Caddy) | core | selected; proxy candidate | Local stack and single-server production | [0037](../decisions/0037-single-server-compose-reference-deployment.md) |

## Recipes (planned; none written yet)

Named in plain text because the `recipes/` area is created with the first real recipe ([ADR 0036](../decisions/0036-technology-catalogue-and-recipes-areas.md)).

| Recipe | Tier | Why a recipe and not a module |
|---|---|---|
| OPA policy engine behind the decision point | recipe | Two rule sets must be kept in sync; most applications do not need it ([0027](../decisions/0027-central-authorization-decision-point.md)) |
| Vault Agent for secret delivery | recipe | Vault is recommended, not mandatory ([0032](../decisions/0032-secrets-from-environment-and-files.md)) |
| RabbitMQ message broker | recipe | The outbox covers durable events without a broker ([0031](../decisions/0031-transactional-outbox-and-locked-jobs.md)) |
| Self-hosted S3 with SeaweedFS | recipe | Managed S3 is the first production recommendation ([0029](../decisions/0029-s3-object-storage-contract.md)) |
| Malware scanning with ClamAV | recipe | Heavy to run; a hook exists on the storage port ([0029](../decisions/0029-s3-object-storage-contract.md)) |
| Production telemetry backend (managed, or a self-hosted collector with Prometheus, Tempo, Loki, Grafana) | recipe | Only the local stack ships ([0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md)) |
| Server-side session store (Redis) | recipe | Documented upgrade path for instant session revocation ([0026](../decisions/0026-nextjs-bff-session.md)) |

## Future

Live multi-tenancy with organization management ([0028](../decisions/0028-tenant-ready-schema.md)), Kubernetes deployment ([0037](../decisions/0037-single-server-compose-reference-deployment.md)), search and caching.

## Considered and not selected

| Technology | Reason |
|---|---|
| MinIO community edition | Repository archived on 2026-04-25 and no longer maintained; source-only distribution ([0029](../decisions/0029-s3-object-storage-contract.md)) |
| MinIO AIStor Free | Proprietary licence ([0029](../decisions/0029-s3-object-storage-contract.md)) |
| Garage | Deliberately narrow S3 API and AGPLv3 ([0029](../decisions/0029-s3-object-storage-contract.md)) |
| SeaweedFS as the local default | Credentials need a config file or shell commands, which is less easy locally; it is the self-hosting recipe ([0029](../decisions/0029-s3-object-storage-contract.md)) |
| Auth.js | Joined Better Auth on 2025-09-22; security patches only ([0026](../decisions/0026-nextjs-bff-session.md)) |
| Better Auth | Keeps its own user and session tables, duplicating Keycloak ([0026](../decisions/0026-nextjs-bff-session.md)) |
| Kafka | RabbitMQ fits typical application workloads better; Kafka suits high-volume streaming ([0031](../decisions/0031-transactional-outbox-and-locked-jobs.md)) |
| OPA as a default component | Extra service, duplicate rules, outage denies everything ([0027](../decisions/0027-central-authorization-decision-point.md)) |
| Redis as a default component | Not needed for sessions or rate limiting at the start ([0026](../decisions/0026-nextjs-bff-session.md)) |
| Spring Cloud Vault | Couples the application to Vault ([0032](../decisions/0032-secrets-from-environment-and-files.md)) |
| Kubernetes and Helm as the reference deployment | Heavy operations cost ([0037](../decisions/0037-single-server-compose-reference-deployment.md)) |
| Maven multi-module per capability | Heavier build; boundaries are checked by tests instead ([0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)) |
| Spring Boot Admin | Present in `pom.xml` by mistake; not a decided capability ([open questions](../decisions/open-questions.md)) |
| Elasticsearch and Kibana | No concrete requirement ([goals and non-goals](../product/goals-and-non-goals.md)) |
