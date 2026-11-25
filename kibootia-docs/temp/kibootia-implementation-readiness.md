# Kibootia — Implementation Readiness Review

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (assessment). Nothing described here exists in the repository except a bare Spring Boot application, MySQL and Adminer in Compose; see [implementation status](../product/roadmap/implementation-status.md). Non-authoritative: the permanent documents and ADRs win in any conflict.

Prepared after the second decision round ([decision log](kibootia-decision-log.md), D-35 to D-75). It assesses whether Kibootia can enter implementation planning. It starts no implementation.

## Verdict

**Kibootia is ready to enter implementation planning for milestone 0.** No decision blocks coding. Two owner actions come before milestone 0 starts: approve the rewritten milestone criteria (section 17), and rotate the credentials that were committed to git history. Several decisions rest on claims that have not been tested; each has a check in the milestone that needs it and a fallback (section 14).

**No coding starts without the owner's explicit approval.**

## 1. Final product scope

Kibootia is a template that applications copy and own ([ADR 0023](../decisions/0023-template-fork-adoption-model.md)): a batteries-included Spring Boot and Next.js foundation with a working reference application. A fresh clone is meant to offer: guided startup of the infrastructure, sign-in by invitation or self-registration, user and role administration, profile and avatar management, an audit log viewer, logs, metrics and traces in Grafana, generated API documentation, and one deletable example feature. The interface is in English. Excluded: microservices, Kubernetes as a requirement, a shipped broker or policy engine, tenant management, published libraries, translations, application notifications, machine clients ([goals and non-goals](../product/goals-and-non-goals.md)).

## 2. Confirmed architecture

A modular monolith with hexagonal boundaries where they matter ([overview](../architecture/overview.md)). The browser talks only to the Next.js server, which is the backend-for-frontend and holds the tokens; Spring is a stateless resource server. Keycloak (with its own PostgreSQL) owns identity; Kibootia's MySQL owns profiles, roles, permissions and business data. The BFF design was re-examined against RFC 10017 and stands ([ADR 0042](../decisions/0042-bff-api-proxy-and-csrf.md)).

## 3. Module boundaries and responsibilities

Fixed root package `com.kibootia.backend`; modules `platform`, `identity`, `access`, `audit`, `files`, `notes`; public contract in the top-level package, private sub-packages; boundaries verified by Spring Modulith, layer rules by ArchUnit ([ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [package structure](../engineering/conventions/package-structure.md)). The frontend mirrors this with one feature folder per module ([frontend](../architecture/frontend.md)). New modules follow the example feature (milestone 5).

## 4. Technology selections and statuses

The [technology catalogue](../technology/README.md) is the single list. Selected since round 2: Java 25 on Eclipse Temurin, Spring Modulith, springdoc-openapi v3, PostgreSQL for Keycloak, OpenTofu, Mailpit, pnpm scripts. Statuses are *selected*, never *verified*: no combination is tested and no CI exists.

## 5. Identity and authorization flow

Sign-in: Authorization Code with PKCE through the Next.js server, tokens kept server-side, HttpOnly session cookie ([authentication](../architecture/identity/authentication.md)). Account lifecycle: invite or self-register, first sign-in creates the profile and assigns the `user` role, email verification enforced in the decision point, disable ends Keycloak sessions ([user lifecycle](../architecture/identity/user-lifecycle.md)). Authorization: permissions in code, editable roles, one decision point with `can` and `scopeFor`, checks on use cases, one indexed query per request, no cache, machine tokens rejected ([authorization](../architecture/identity/authorization.md)).

## 6. Data and storage architecture

MySQL with JPA/Hibernate and Flyway forward-only migrations in per-module folders; UUIDv7 as `BINARY(16)`; `tenant_id` with a Hibernate discriminator on a single default tenant; audit columns, optimistic locking, soft delete with a retention purge ([persistence stack](../architecture/data/persistence-stack.md)). Files: S3 API contract, RustFS locally, streamed through the backend, byte-level type detection, avatar re-encoding ([ADR 0029](../decisions/0029-s3-object-storage-contract.md)). Audit entries reference people by ID only ([audit log](../architecture/audit-log.md)).

## 7. API and frontend conventions

Code-first OpenAPI committed as `openapi.json` with a CI drift check; `/api/v1`; RFC 9457 Problem Details with `code`, `traceId`, `errors`; fixed status mapping; paged lists with sort allow-lists ([API conventions](../architecture/api/conventions.md)). Frontend: App Router, generated client, one `/api/*` proxy for browser calls, TanStack Query, React Hook Form with Zod, English text, WCAG 2.2 AA ([frontend](../architecture/frontend.md)).

## 8. Observability and background processing

OTLP-only telemetry into a local Grafana LGTM container, console JSON logs, health on an internal management port ([observability baseline](../operations/monitoring/observability-baseline.md)). Durable events through Spring Modulith's registry as the outbox, lock-protected scheduled jobs, no broker ([communication](../architecture/communication.md)).

## 9. Testing and quality strategy

Unit, Testcontainers integration, API, security, architecture, frontend and Playwright tests; `*Test` and `*IT` naming; per-module builders; the seven-point feature [definition of done](../engineering/testing/verification-standard.md#definition-of-done-for-a-feature); coverage reported without a gate; CI on GitHub Actions is the only evidence for "verified".

## 10. Local developer experience

Infrastructure in Compose, applications on the host (or all in the `apps` profile); `pnpm setup`, `infra:up`, `dev`, `reset`; generated local secrets; an 8 GB machine as the practical minimum ([local setup](../engineering/getting-started/local-setup.md)). The guide describes the target; the current steps are observed and unverified.

## 11. Production deployment expectations

A single VM with Docker Compose, a TLS proxy, Keycloak on `auth.<domain>` with its admin console blocked, separate migration and realm steps, secrets from files or the environment, backups of MySQL, PostgreSQL and storage taken consistently ([container deployment](../operations/deployment/container-deployment.md)). Not horizontally scalable by itself. Vault, a broker, OPA and a production telemetry backend are recipes.

## 12. Security requirements

Audience and issuer validation, deny-by-default with no public endpoint on the API port, user-bound tokens only, CSRF rule on the proxy, email verification, immediate denial of disabled users, least-privilege Keycloak service account, upload validation, rate limiting, no secrets or personal data in logs or audit entries, production refusing development defaults. The [threat model](../operations/security/threat-model.md) is kept current per milestone. Known weakness today: credentials in git history and a hardcoded datasource password ([secrets management](../operations/security/secrets-management.md)).

## 13. Remaining deferred decisions

| Item | Why deferred | Needed by |
|---|---|---|
| Licence (OQ-48) | Owner chose to decide later | First release, milestone 6 |
| Machine-to-machine clients | No current use | A future recipe |
| Translations, application notifications, backend mail | No current requirement | Future |
| Production SMTP guidance for Keycloak (OQ-36) | Provider-neutral documentation | Milestone 6 |
| Example feature name (OQ-37) | Cosmetic | Milestone 5 |
| Live multi-tenancy, OPA, broker, Vault, Redis sessions, Kubernetes | Recipes or future tier | On concrete need |

## 14. Technical verification tasks

Nothing below has been run. Each is a build or test in the milestone shown, and a failure triggers the fallback through a new ADR.

| Task | Open question | Milestone | Fallback |
|---|---|---|---|
| Whole toolchain builds on Java 25 | OQ-44 | 0 | Java 21 |
| Spring Modulith 2.1.x with Spring Boot 4.1.x; registry on MySQL via Flyway | OQ-43 | 0 | Hand-written outbox |
| OTLP log export in the chosen Spring Boot | OQ-42 | 1 | Console JSON logs only |
| Keycloak hostname and back channel; Spring issuer handling in the `apps` profile | OQ-45 | 1 | Applications on the host only |
| OpenTofu Keycloak provider with the pinned Keycloak; Keycloak on the pinned PostgreSQL | OQ-46 | 1 | Earlier compatible versions |
| Hibernate UUIDv7, `BINARY(16)`, soft delete | OQ-41 | 2 | Application-side generation or explicit mapping |
| springdoc v3 exact version with Spring Boot 4.1 | — | 2 | Nearest working version |
| RustFS presigned operations | OQ-40 | 4 | SeaweedFS |
| OpenTofu native state locking on the production S3 service | OQ-47 | 6 | Root-only state file |

Research behind round 2 used official or primary sources where available; the Spring Boot 4.1 Java range came from a third-party table and should be confirmed against Spring's documentation in milestone 0.

## 15. Updated documentation inventory

- **New ADRs:** [0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) to [0048](../decisions/0048-java-25-baseline-on-temurin.md).
- **ADR status changes:** 0005 and 0021 accepted; 0019 deprecated (superseded by 0048); notes added to 0008, 0013, 0020, 0024, 0025, 0026, 0027, 0029, 0031.
- **New documents:** [frontend](../architecture/frontend.md), [user lifecycle](../architecture/identity/user-lifecycle.md), [audit log](../architecture/audit-log.md), four glossary terms, this review.
- **Updated:** open questions, ADR index and README, architecture (overview, layers, enforcement, communication, context, API, identity, data), package structure, local setup, testing strategy and verification standard, application configuration, container deployment, observability, secrets, threat model, technology catalogue, vision, goals and non-goals, roadmap and all seven milestones, implementation status, decision log, root README, project definition (marked historical).
- **Unchanged on purpose:** accepted ADRs' decisions; implementation status values (everything still planned or observed).

## 16. Implementation dependencies and recommended sequence

Milestones M0 to M6 keep their order ([roadmap](../product/roadmap/README.md)): M0 (cleanup, Java 25, module skeleton, CI) → M1 (walking skeleton: Keycloak, BFF, proxy, traces, first Dockerfiles) → M2 (persistence, tenancy, API conventions, outbox, decision point, audit) → M3 (users, roles, invitations) → M4 (files) → M5 (example feature, removal matrix) → M6 (production, recipes, release). Dependencies: M1 needs M0's CI and Java baseline; M2's decision point and audit module precede M3; M3's profile permission precedes M4's avatar visibility; M5 needs the foundation modules for its boundary rules to be meaningful. Verification tasks (section 14) are the first work item of the milestone that lists them.

## 17. Milestone criteria for approval (decision D-73)

All seven milestones' completion criteria were rewritten on 2026-11-08 and remain *proposed* until approved. What changed:

| Milestone | Main changes |
|---|---|
| 0 | Java 25 with a fallback check, Spring Modulith check, module skeleton, `pnpm setup`, the Java blocker removed |
| 1 | Keycloak on PostgreSQL with OpenTofu realm step, Mailpit, `/api` proxy tests, management-port health, first Dockerfiles and the `apps` profile |
| 2 | Committed `openapi.json` with drift check, Problem Details, decision point and `@NoAuthorization` rule, audit module, Modulith-based outbox |
| 3 | Self-registration and invitations, default `user` role, email verification behavior, immediate denial of disabled users, `identity.profiles.view` |
| 4 | Avatar visibility through the new permission, streaming through the proxy |
| 5 | Meets the full definition of done; Modulith and authorization rules meaningful |
| 6 | Keycloak on `auth.<domain>`, OpenTofu production step and state, consistent backups, licence chosen |

**Classification of what remains**

- **Blocking before coding:** owner approval of the criteria; credential rotation (owner task).
- **Non-blocking (decide during implementation):** exact version pins, rate-limiter and job-lock libraries, image-processing library, avatar sizes and limits, demo account names, shipped Grafana dashboards, Keycloak login theme.
- **Deferred:** section 13.
