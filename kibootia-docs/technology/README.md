# Technology Catalogue

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

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
| Spring Boot | core | selected | Backend application | [0002](../decisions/0002-modular-monolith.md) |
| Java 25 (LTS) on Eclipse Temurin | core | selected; fallback Java 21 if the milestone 0 toolchain check fails | Language runtime and container base image | [0048](../decisions/0048-java-25-baseline-on-temurin.md) |
| Spring Modulith | core | selected; build check open (Spring Boot 4.1 with Modulith 2.1.x) | Module boundary verification and the event publication registry (the outbox) | [0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) |
| Maven | core | selected | Backend build | [project definition](../temp/kibootia-project-definition.md) |
| Next.js (App Router), TypeScript, pnpm | core | selected | Frontend and backend-for-frontend | [0038](../decisions/0038-frontend-stack.md), [0026](../decisions/0026-nextjs-bff-session.md) |
| Tailwind CSS, shadcn/ui | core | selected | Frontend styling and components | [0038](../decisions/0038-frontend-stack.md) |
| `openid-client` | core | selected | OpenID Connect in the Next.js server | [0026](../decisions/0026-nextjs-bff-session.md) |
| TanStack Query, React Hook Form, Zod | core | selected | Frontend data and forms | [0038](../decisions/0038-frontend-stack.md) |
| Generated OpenAPI client | core | selected; generator candidate: openapi-typescript with openapi-fetch | Typed API calls, generated from the committed `openapi.json` | [0038](../decisions/0038-frontend-stack.md), [0043](../decisions/0043-http-api-conventions.md) |
| springdoc-openapi (v3 line) | core | selected; exact version pinned at implementation | OpenAPI generation and the local Swagger UI | [0043](../decisions/0043-http-api-conventions.md) |
| Keycloak with OpenID Connect | core | selected | Identity, user lifecycle and identity email | [0006](../decisions/0006-keycloak-oidc-identity.md), [0025](../decisions/0025-identity-and-authorization-data-split.md), [0020](../decisions/0020-keycloak-in-local-compose.md), [0041](../decisions/0041-user-lifecycle-and-account-model.md) |
| PostgreSQL (for Keycloak only) | core | selected; version pinned from Keycloak's supported list | Keycloak's own database | [0045](../decisions/0045-keycloak-operations.md) |
| OpenTofu with the Keycloak provider | core | selected; run as a one-shot container | Realm configuration as code | [0045](../decisions/0045-keycloak-operations.md) |
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
| Transactional outbox, lock-protected jobs | core | selected; outbox is the Spring Modulith event registry (hand-written table as fallback); job-lock library candidate: ShedLock | Durable events and single-run jobs | [0031](../decisions/0031-transactional-outbox-and-locked-jobs.md), [0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) |
| OpenTelemetry starter, Micrometer, Actuator | default-on | selected | Logs, metrics, traces over OTLP; health | [0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md) |
| Grafana LGTM container (local) | default-on | selected for local development only | Receives and shows telemetry | [0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md) |
| User and role administration with audit log | default-on | selected | Administration interface, runtime-editable roles | [0025](../decisions/0025-identity-and-authorization-data-split.md) |
| Bucket4j 8.16.1 with Caffeine (in-app rate limiter) | default-on | selected | Per-user token buckets in a size-bounded cache; 8.16.1 is the newest line with a patch release | [0050](../decisions/0050-milestone-3-implementation-decisions.md) |
| Object storage module (S3 API) on the AWS SDK for Java 2.57 | default-on | selected; built in milestone 4 (observed, CI run pending) | Avatars and uploads | [0029](../decisions/0029-s3-object-storage-contract.md), [0051](../decisions/0051-milestone-4-implementation-decisions.md) |
| RustFS 1.0.4 (local S3 server) | default-on | selected; the verification gate passed in a local run on 2026-11-10 (12 contract tests, presigned PUT and GET included); CI run pending | Local object storage | [0029](../decisions/0029-s3-object-storage-contract.md), [0051](../decisions/0051-milestone-4-implementation-decisions.md) |
| Apache Tika (`tika-core` 3.3.3) | default-on | selected | File type from the bytes | [0051](../decisions/0051-milestone-4-implementation-decisions.md) |
| TwelveMonkeys ImageIO (WebP, JPEG, metadata) 3.15.3 | default-on | selected | Reads WebP and EXIF orientation; images are re-encoded with the JDK's PNG writer | [0051](../decisions/0051-milestone-4-implementation-decisions.md) |
| Mailpit (local mail catcher) | default-on | selected for local development only | Receives Keycloak's identity email locally | [0041](../decisions/0041-user-lifecycle-and-account-model.md) |
| Adminer (pinned) | default-on | selected for local development only | Database browser | [0040](../decisions/0040-local-development-topology-and-setup.md) |
| Swagger UI (bundled with springdoc) | default-on | selected; disabled in production by default | Interactive API documentation locally | [0043](../decisions/0043-http-api-conventions.md) |
| Example feature ("Notes") | default-on, meant to be removed | selected | Shows how to add a feature | [0024](../decisions/0024-package-modules-with-infrastructure-toggles.md) |
| Docker Compose with a TLS reverse proxy (for example Caddy) | core | selected; proxy candidate | Local stack and single-server production | [0037](../decisions/0037-single-server-compose-reference-deployment.md), [0040](../decisions/0040-local-development-topology-and-setup.md) |
| pnpm scripts with Node scripts | core | selected | Cross-platform setup, start and reset | [0040](../decisions/0040-local-development-topology-and-setup.md) |

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
| Full change history of every record (Envers-style) | recipe | The audit log covers security and administrative events ([0046](../decisions/0046-audit-log-design.md)) |
| Server-side session store (Redis) | recipe | Documented upgrade path for instant session revocation ([0026](../decisions/0026-nextjs-bff-session.md)) |

## Future

Live multi-tenancy with organization management ([0028](../decisions/0028-tenant-ready-schema.md)), Kubernetes deployment ([0037](../decisions/0037-single-server-compose-reference-deployment.md)), search and caching, application notifications and a backend mail module, service principals for machine clients ([0044](../decisions/0044-authorization-enforcement-conventions.md)), interface translations.

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
| Spring Boot Admin | Present in `pom.xml` by mistake; not a decided capability; removed in milestone 0 ([open questions](../decisions/open-questions.md)) |
| keycloak-config-cli | Maintained and idempotent, but the owner chose OpenTofu for realm configuration ([0045](../decisions/0045-keycloak-operations.md)) |
| Terraform | Business Source License; OpenTofu is the open-source drop-in ([0045](../decisions/0045-keycloak-operations.md)) |
| Spring Cloud Gateway as the BFF | A third deployable and a session store; the Next.js server is the BFF ([0042](../decisions/0042-bff-api-proxy-and-csrf.md)) |
| Tokens held in the browser | Ranked least secure by RFC 10017 ([0042](../decisions/0042-bff-api-proxy-and-csrf.md)) |
| Spring Boot buildpacks, Jib | Less transparent than explicit Dockerfiles, and Next.js still needs a Dockerfile ([0047](../decisions/0047-health-exposure-and-container-images.md)) |
| MapStruct | Hand-written mappers are decided ([0005](../decisions/0005-hand-written-model-mappers.md)) |
| next-intl (or any translation library) | The UI is English only ([0038](../decisions/0038-frontend-stack.md) with the decision log, D-69) |
| A backend mail module | Keycloak sends all identity email ([0041](../decisions/0041-user-lifecycle-and-account-model.md)) |
| Elasticsearch and Kibana | No concrete requirement ([goals and non-goals](../product/goals-and-non-goals.md)) |
