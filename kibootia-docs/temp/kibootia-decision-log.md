# Kibootia — Decision Log (rounds 1 and 2)

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (working log). Non-authoritative: the permanent documents and ADRs that came out of these decisions win in any conflict.

Working record of the interview that followed [the ideation brief](kibootia-ideation-decisions.md), which is left unchanged as the historical record. Each decision was put to the owner with options and trade-offs before it was recorded. "Delegated" means the owner asked Claude to decide; the owner may override.

The last column shows where each decision was written down permanently.

## Decisions

| ID | Topic | Status | Decision | Alternatives rejected | Permanent home |
|---|---|---|---|---|---|
| D-01 | Adoption model | confirmed | Template/fork; apps own the code; no published packages | Hybrid, shared library, generator | [ADR 0023](../decisions/0023-template-fork-adoption-model.md) |
| D-01a | Fork style | confirmed | Clone with an `upstream` remote; upgrade by merging release tags; CHANGELOG with upgrade and security notes | GitHub template button | ADR 0023 |
| D-02 | Module mechanism | confirmed | One Maven project; packages checked by ArchUnit; on/off property, migration folder and Compose profile for infrastructure-backed modules; tested removal recipes | Maven multi-module; delete-only packages | [ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md) |
| D-03 | Identity boundary | confirmed | Keycloak owns identity and user lifecycle (Admin API behind a port, least-privilege service account); Kibootia DB owns profile, roles, permissions, grants | Keycloak-centric roles; app roles with no Admin API | [ADR 0025](../decisions/0025-identity-and-authorization-data-split.md) |
| D-04 | Frontend login flow | confirmed | Next.js as backend-for-frontend; tokens server-side; HttpOnly cookie | Browser-held tokens; Spring as BFF | [ADR 0026](../decisions/0026-nextjs-bff-session.md) |
| D-05 | Authorization and OPA | confirmed | One decision point in Java; permissions defined in code; OPA as recipe only | OPA shipped but off; no seam; OPA default | [ADR 0027](../decisions/0027-central-authorization-decision-point.md) |
| D-06 | Editable roles | confirmed | Admins edit roles at runtime; protected admin role, last-admin safeguard, audit log | Fixed roles; direct user grants | ADR 0025 |
| D-07 | Multi-tenancy | confirmed | Tenant-ready schema: `tenant_id`, `@TenantId`, one default tenant, isolation tests; no tenant UI | None; seams only; tenant-aware default | [ADR 0028](../decisions/0028-tenant-ready-schema.md) |
| D-08 | Example feature | confirmed | One deletable generic example ("Notes", name not final) | None; separate examples folder | ADR 0024 and the [roadmap](../product/roadmap/README.md) |
| D-09 | Reference app extras | confirmed | Audit log viewer, email invitations (local mail catcher), generated API docs UI | Background job demo (not selected) | [goals and non-goals](../product/goals-and-non-goals.md) |
| D-10 | Object storage | **delegated** | S3 API as the contract; RustFS local default behind a verification gate; managed S3 first in production; SeaweedFS recipe | MinIO (community edition archived 2026-04-25); AIStor Free | [ADR 0029](../decisions/0029-s3-object-storage-contract.md) |
| D-11 | File transfer | confirmed | Stream through the backend by default; presigned URLs as an opt-in | Presigned by default | ADR 0029 |
| D-12 | Avatar visibility | confirmed | Signed-in users of the same tenant, through the decision point | Public; owner and admins only | ADR 0029 |
| D-13 | File processing | **delegated** | Byte-level type detection and size limits mandatory; avatar re-encode and metadata strip; orphan reconciliation job; malware scan is a recipe | — | ADR 0029 |
| D-14 | Observability shape | confirmed | OTLP-only app; local Grafana LGTM container; console JSON logs; Actuator health | Separate containers; classic pull model | [ADR 0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md) |
| D-15 | Observability default | confirmed | On by default; can be switched off | Opt-in profile | ADR 0030 |
| D-16 | Events and jobs | confirmed | Transactional outbox in MySQL plus lock-protected jobs; no broker; RabbitMQ recipe | Keep ADR 0014 as is; optional RabbitMQ module; broker by default | [ADR 0031](../decisions/0031-transactional-outbox-and-locked-jobs.md) |
| D-17 | Security baseline | **delegated** | Audience check, deny-by-default, realm hardening, BFF cookie and CSRF rules, no CORS, fail closed without keys, production refuses dev defaults | — | [token validation](../architecture/identity/token-validation.md), [ADR 0008](../decisions/0008-audience-validation-and-deny-by-default.md) |
| D-18 | Rate limiting | confirmed | In-app per-instance limiter on sensitive endpoints; edge limiting required in production | Redis; edge only | [API conventions](../architecture/api/conventions.md) |
| D-19 | Threat model | confirmed | Living, per-trust-boundary document | Later; none | [threat model](../operations/security/threat-model.md) |
| D-20 | Production deployment | confirmed | Single-VM Docker Compose reference with automatic TLS | Kubernetes and Helm; guidance only | [ADR 0037](../decisions/0037-single-server-compose-reference-deployment.md) |
| D-21 | Production secrets | confirmed | Env vars or files only; Vault recommended, not mandatory | Vault mandatory; Spring Cloud Vault | [ADR 0032](../decisions/0032-secrets-from-environment-and-files.md) |
| D-22 | Identifiers | confirmed | UUIDv7 stored as `BINARY(16)` | Auto-increment; random UUID | [ADR 0033](../decisions/0033-uuidv7-identifiers.md) |
| D-23 | Delete policy | confirmed, **against recommendation** | Soft delete on business tables with retention purge and restore. Claude recommended hard delete plus audit; the owner chose this after the risks were set out | Hard delete; opt-in soft delete; soft delete with no purge | [ADR 0034](../decisions/0034-soft-delete-with-retention-purge.md) |
| D-24 | Persistence package | confirmed | Audit columns, optimistic locking, per-module migrations, forward-only, separate migrate step in production, local-only seed, offset paging | — | [schema migrations](../architecture/data/schema-migrations.md), [persistence stack](../architecture/data/persistence-stack.md) |
| D-25 | BFF OIDC library | confirmed | `openid-client` plus a thin tested BFF module | Auth.js (maintenance mode); Better Auth (second user store) | ADR 0026 |
| D-26 | BFF session store | confirmed | Encrypted cookie; Redis is the documented upgrade | Redis from the start | ADR 0026 |
| D-27 | Frontend stack | confirmed | App Router, strict TypeScript, pnpm, Tailwind with shadcn/ui, generated OpenAPI client, TanStack Query, React Hook Form with Zod, Vitest and Playwright | — | [ADR 0038](../decisions/0038-frontend-stack.md) |
| D-28 | CI package | confirmed | GitHub Actions, module-removal matrix, scanning, Renovate, trunk-based, Conventional Commits | — | [verification standard](../engineering/testing/verification-standard.md) |
| D-29 | Version policy | confirmed | Adopt a new minor or major only after its first patch | Latest immediately; N-1 | [ADR 0035](../decisions/0035-version-adoption-policy.md) |
| D-30 | End-to-end cadence | confirmed | Nightly, before release tags, and on PRs labelled `e2e` | Every PR | verification standard |
| D-31 | Catalogue and recipes | confirmed | New `technology/` and `recipes/` areas; catalogue owns tier and selection, links to implementation status | Inside `engineering/`; one document | [ADR 0036](../decisions/0036-technology-catalogue-and-recipes-areas.md) |
| D-32 | Rejected technologies | confirmed | Catalogue lists "considered, not selected" rows with reasons | — | [technology catalogue](../technology/README.md) |
| D-33 | Roadmap | confirmed | Seven vertical-slice milestones M0 to M6 replace the six layer-based drafts | — | [roadmap](../product/roadmap/README.md) |
| D-34 | First admin | confirmed | Demo admin locally; one-time bootstrap email setting in production, only while no admin exists, audited | Operator command | ADR 0025 |

## Still open or deferred

Tracked in [open questions](../decisions/open-questions.md): Java version (deferred by the owner, blocks M0), mapper approach, frontend location confirmation, API error format, configuration naming, test naming, image build, health exposure, permission-cache invalidation, production SMTP provider, example feature name.

Technical claims to verify before the related capability is built: RustFS presigned URLs (D-10), Hibernate UUIDv7 and soft-delete support (D-22, D-23), OTLP log export wiring in Spring Boot 4.1 (D-14), Spring Modulith compatibility (D-02, D-16).

## Research behind D-10 and D-25 (2026-10-28)

- The MinIO community repository was archived on 2026-04-25 and its README says it is no longer maintained; the community edition is distributed as source only.
- Auth.js joined Better Auth on 2025-09-22; existing users get security patches and new projects are pointed to Better Auth, which keeps its own user and session tables.
- Presigned-URL support was not confirmed in the official documentation of RustFS or SeaweedFS; this is why D-10 has a verification gate.


---

# Round 2 — Pre-implementation decisions (2026-11-08)

Working record of the second interview, held before any implementation. Same rules as round 1: each decision was put to the owner with options and trade-offs; "delegated" means the owner asked Claude to decide; this log is non-authoritative and the ADRs and permanent documents win in any conflict. **The list "Still open or deferred" above is superseded by this round**; the current list is in [open questions](../decisions/open-questions.md).

## Decisions

| ID | Topic | Status | Decision | Alternatives rejected | Permanent home |
|---|---|---|---|---|---|
| D-35 | Module enforcement and outbox | confirmed | Spring Modulith verifies module boundaries and its event publication registry is the outbox; ArchUnit keeps layer rules inside modules | Own outbox with Modulith checks only (kept as fallback); ArchUnit only | [ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md) |
| D-36 | Module map | confirmed | Fixed root package; modules `platform`, `identity`, `access`, `audit`, `files`, `notes`; public contract in the top-level package | One merged `users` module | ADR 0039, [package structure](../engineering/conventions/package-structure.md) |
| D-37 | Local topology | confirmed | Infrastructure in Compose; applications on the host daily; an `apps` Compose profile runs both in containers | Everything in Compose; applications on the host only | [ADR 0040](../decisions/0040-local-development-topology-and-setup.md) |
| D-38 | Setup tooling | confirmed | Root pnpm scripts backed by Node scripts: `setup`, `infra:up`, `dev`, `reset` | `just`; Makefile plus PowerShell | ADR 0040 |
| D-39 | Adminer | confirmed | Keep, pin an exact version, on by default locally, never in production | Remove; opt-in profile | ADR 0040 |
| D-40 | Account creation | confirmed, **differs from recommendation** | Invitations and self-registration are both on by default. Claude recommended invite-only with self-registration as a switch | Invite-only (with or without a switch) | [ADR 0041](../decisions/0041-user-lifecycle-and-account-model.md) |
| D-41 | Default role | confirmed | Built-in `user` role assigned at first sign-in; own-profile actions by ownership | No default role | ADR 0041 |
| D-42 | Profile data | confirmed | Keycloak owns name and email; Kibootia keeps a read copy refreshed at sign-in and on admin edits | Kibootia UI edits and pushes to Keycloak | ADR 0041 |
| D-43 | Email | confirmed | Keycloak sends all identity mail; no backend mail module | A backend mail module now | ADR 0041 |
| D-43a | Disabled users | confirmed (derived; owner did not object) | Sessions ended in Keycloak; backend refuses disabled profiles on the next request | — | ADR 0041 |
| D-44 | Email verification | confirmed | Required by default, enforced in the decision point so operations can later be allowed unverified | Keycloak's login-blocking setting | ADR 0041 |
| D-45 | Profile visibility | confirmed | Viewing other users needs `identity.profiles.view`, within the tenant | Keep D-12 (all signed-in users of the tenant) | ADR 0041 (extends ADR 0029) |
| D-46 | CSRF | confirmed | Matching `Origin` plus a required custom header on non-GET/HEAD requests | Synchronizer token | [ADR 0042](../decisions/0042-bff-api-proxy-and-csrf.md) |
| D-47 | BFF request path | confirmed (re-examined at the owner's request) | ADR 0026 reaffirmed; one generic `/api/*` proxy; server components call Spring directly | Spring Cloud Gateway BFF; tokens in the browser; per-feature handlers and Server Actions | ADR 0042 |
| D-48 | API contract | confirmed | Code-first springdoc-openapi v3, committed `openapi.json`, CI drift check | Uncommitted generation; design-first | [ADR 0043](../decisions/0043-http-api-conventions.md) |
| D-49 | Error format | confirmed | RFC 9457 Problem Details with `code`, `traceId`, `errors` | Custom envelope | ADR 0043 |
| D-50 | URLs and versioning | confirmed | `/api/v1/…`, plural kebab-case, sub-resource actions | No version segment | ADR 0043 |
| D-51 | Status codes and lists | confirmed | Status mapping, `{items, page}` lists, sort allow-list, named filters, Swagger UI local-only | 422 for business rules | ADR 0043 |
| D-52 | Check site | confirmed | Checks on use-case methods; `@RequiresPermission` and `authorization.require`; ArchUnit rule | Checks on controllers | [ADR 0044](../decisions/0044-authorization-enforcement-conventions.md) |
| D-53 | List filtering | confirmed | `scopeFor(action)` returns ALL, OWN or NONE | Each module decides | ADR 0044 |
| D-54 | Permission freshness | confirmed | No cache; one query per request | Local invalidation; time-based cache | ADR 0044 |
| D-55 | Machine clients | confirmed (deferred) | Rejected by default; service principals are a future recipe | Support now | ADR 0044 |
| D-56 | Keycloak database | confirmed, **differs from recommendation** | Keycloak uses its own PostgreSQL. Claude recommended a separate database on the same MySQL | Same MySQL; development database locally | [ADR 0045](../decisions/0045-keycloak-operations.md) |
| D-57 | Realm configuration | confirmed, **differs from recommendation** | OpenTofu with the Keycloak provider as a one-shot container. Claude recommended keycloak-config-cli | keycloak-config-cli; import and reset only; Terraform | ADR 0045 |
| D-57a | OpenTofu state | **delegated** | Local volume; production private encrypted S3 bucket with locking; root-only file as fallback | State file on the server by default | ADR 0045 |
| D-58 | Keycloak exposure | confirmed | `auth.<domain>`; admin console blocked, SSH tunnel | Path on the main domain; public admin console | ADR 0045 |
| D-59 | Audit scope | confirmed | Security and administrative events plus a module API | Envers-style full history (recipe) | [ADR 0046](../decisions/0046-audit-log-design.md) |
| D-60 | Audit personal data | confirmed | User IDs only; automatic pseudonymisation after erasure | Names and emails in entries | ADR 0046 |
| D-61 | Audit retention | confirmed | Configurable, default 365 days | Keep forever | ADR 0046 |
| D-62 | Audit write timing | confirmed | Same transaction as the change | Via the outbox after commit | ADR 0046 |
| D-63 | Health | confirmed | Internal management port, health only, readiness checks MySQL | Public health; authenticated health | [ADR 0047](../decisions/0047-health-exposure-and-container-images.md) |
| D-64 | Images | confirmed | Explicit multi-stage non-root Dockerfiles; CI build and scan | Buildpacks; Jib | ADR 0047 |
| D-65 | Mappers | confirmed | Hand-written mappers | MapStruct | [ADR 0005](../decisions/0005-hand-written-model-mappers.md) |
| D-66 | Frontend location | confirmed | `frontend/` beside `backend/`, in the root pnpm workspace | — | [ADR 0021](../decisions/0021-frontend-beside-backend-in-one-repository.md) |
| D-67 | Configuration naming | confirmed | `kibootia.<module>.<setting>`, typed and validated, one catalogue | — | [application configuration](../operations/configuration/application-configuration.md) |
| D-68 | Test naming and data | confirmed | `*Test`, `*IT`, `@DisplayName`, per-module builders | — | [testing strategy](../engineering/testing/strategy.md) |
| D-69 | Languages | confirmed, **differs from recommendation** | English only with hard-coded text. Claude recommended a translation-ready setup | Translation-ready; several languages | [frontend](../architecture/frontend.md), [goals and non-goals](../product/goals-and-non-goals.md) |
| D-70 | Licence | deferred | Undecided; blocks the first release only | Apache-2.0 (recommended); MIT | [open questions](../decisions/open-questions.md) (OQ-48) |
| D-71 | Definition of done | confirmed | The seven-point feature checklist | Without per-feature end-to-end and accessibility checks | [verification standard](../engineering/testing/verification-standard.md#definition-of-done-for-a-feature) |
| D-72 | Coverage | confirmed | Reported, no gate | An 80% gate | verification standard |
| D-73 | Milestone criteria | confirmed | Claude rewrites them; the owner approves them in the readiness review | Keep as proposed | [roadmap](../product/roadmap/README.md) |
| D-74 | Java | confirmed | Java 25 (LTS); fallback Java 21 if the milestone 0 toolchain check fails | Java 21; Java 17 | [ADR 0048](../decisions/0048-java-25-baseline-on-temurin.md) (supersedes ADR 0019) |
| D-75 | JDK distribution | confirmed | Eclipse Temurin | Any certified OpenJDK | ADR 0048 |

## Context, rationale and implications by theme

**Modules (D-35, D-36).** Every class lands in this layout, and forks merge upstream releases, so the root package is fixed. Spring Modulith removes the riskiest hand-written code (outbox retries) at the cost of one more dependency that tracks Spring Boot. Tenant and trace context travel inside the event payload because the registry stores events as serialised data. Implication: a milestone 0 build check, with a hand-written outbox as the stated fallback.

**Local development (D-37 to D-39).** Running applications on the host avoids the Keycloak issuer-URL mismatch (browser `localhost` versus container service name) in daily work; the `apps` profile solves it once, behind a test. Node and pnpm are needed for the frontend anyway, so pnpm-based setup adds no prerequisite. Trade-off: two documented ways to run, and an 8 GB machine as the practical minimum.

**User lifecycle (D-40 to D-45).** Open self-registration made two earlier decisions risky: avatars visible to every signed-in user of the tenant would let strangers browse the directory (hence `identity.profiles.view`), and unverified addresses would let accounts be registered in other people's names (hence required verification, enforced in Kibootia's decision point so that it stays configurable per operation). The contradiction about who sends mail is resolved in favour of Keycloak. Trade-off: production deployments that keep open sign-up must review bot protection and rate limits.

**BFF and API (D-46 to D-51).** At the owner's request the premise "the browser never holds a token" was re-examined against RFC 10017 (IETF Best Current Practice, August 2026), which lists the BFF first in decreasing order of security, "strongly recommended for business applications", and calls tokens in the browser vulnerable to all attack scenarios it discusses. The decision stands; the open part was which server is the BFF and how browser calls reach Spring. Implication: the proxy is security-critical and needs its own tests.

**Authorization (D-52 to D-55).** Most authorization defects are a missing or misplaced check, so the rule is written down and enforced by an ArchUnit test. The no-cache choice costs one indexed query per request and makes role changes and user disabling immediate on any number of instances.

**Keycloak operations (D-56 to D-58).** Two of the three choices differ from the recommendation. Consequences accepted: two database engines to run, monitor and back up, with consistent backups needed so Keycloak users and Kibootia profiles stay in step; OpenTofu state holds secrets in plain text and needs protected storage. OpenTofu rather than Terraform keeps the template free of a licence question.

**Audit (D-59 to D-62).** Referring to people by ID only resolves the conflict between an append-only log and privacy erasure. Writing in the same transaction removes any window in which a change exists without its entry.

**Operations (D-63, D-64).** With health on an internal port, the API port has no public endpoint at all, tightening ADR 0008.

**Java (D-74, D-75).** Free Temurin updates run to at least October 2027 for 17, December 2029 for 21 and September 2031 for 25. Java 25 has had over a year of patch releases and, with JEP 491, no longer pins virtual threads on `synchronized`. The Java decision was reopened by the owner for this round.

## Research behind the decisions (2026-11-08)

- Spring Modulith: the 2.1.x line targets Spring Boot 4.1; 2.1.1 was released in August 2026 ([release post](https://spring.io/blog/2026/08/26/spring-modulith-2-2-m1-2-1-1-2-0-8-and-1-4-13-released/)). Compatibility with the exact versions in use is not proven.
- springdoc-openapi: the v3 line is for Spring Boot 4 ([project README](https://github.com/springdoc/springdoc-openapi)); the exact pairing with Boot 4.1 needs a version check.
- RFC 10017, *OAuth 2.0 for Browser-Based Apps* ([datatracker](https://datatracker.ietf.org/doc/rfc10017/)): BFF first in decreasing order of security; token-mediating backend "less secure than a BFF"; browser-held tokens "vulnerable to all attack scenarios". The page text was read up to its first 100,000 characters.
- Keycloak: MySQL 8.4 and PostgreSQL are in the supported-database list ([supported configurations](https://www.keycloak.org/server/supported-configurations)); keycloak-config-cli v6.5.x targets Keycloak 26.x ([downloads](https://adorsys.github.io/keycloak-config-cli/downloads)). Neither was tested with Kibootia.
- Java: Temurin availability dates from the [Adoptium support page](https://adoptium.net/support) (the Java 17 date was truncated in the snapshot found; an older page gives October 2027) and [Red Hat's life-cycle tables](https://access.redhat.com/articles/1299013). Spring Boot 4.1 Java range from a [third-party compatibility table](https://stevenpg.com/references/spring-version-compatibility/); not confirmed against the official Spring Boot documentation.
- Not verified: OpenTofu's Keycloak provider against Keycloak 26.x, and OpenTofu native state locking on any specific S3 server.

## Technical claims to verify (by milestone)

| Check | Milestone | Fallback |
|---|---|---|
| Java 25 toolchain, OQ-44 | 0 | Java 21 |
| Spring Modulith 2.1.x with Spring Boot 4.1.x, registry on MySQL via Flyway, OQ-43 | 0 | Hand-written outbox |
| OTLP log export, OQ-42 | 1 | Console JSON logs |
| Keycloak hostname and back channel in the `apps` profile, OQ-45 | 1 | Applications on the host only |
| OpenTofu Keycloak provider and Keycloak on PostgreSQL, OQ-46 | 1 | Earlier compatible versions |
| Hibernate UUIDv7, `BINARY(16)`, soft delete, OQ-41 | 2 | Application-side generation or explicit mapping |
| RustFS presigned operations, OQ-40 | 4 | SeaweedFS |
| OpenTofu S3 native locking, OQ-47 | 6 | Root-only state file |
