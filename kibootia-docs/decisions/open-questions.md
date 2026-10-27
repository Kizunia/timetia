# Open Questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (list).

Live list of unresolved decisions, provisional choices and deviations observed in the repository. Remove an entry (and note it in the relevant document or ADR) when it is resolved. All entries are owned by ishanKulkarni13 until reassigned.

## Provisional decisions awaiting confirmation

| ID | Question | Current provisional answer | Where |
|---|---|---|---|
| OQ-01 | Mapper approach | Hand-written mappers | [ADR 0005](0005-hand-written-model-mappers.md) |
| OQ-02 | Audience check and deny-by-default | Both required | [ADR 0008](0008-audience-validation-and-deny-by-default.md) |
| OQ-03 | Role source | Keycloak realm roles | [ADR 0010](0010-keycloak-realm-roles-as-role-source.md) |
| OQ-04 | How production secrets reach the app | Platform injection, no Vault client | [ADR 0016](0016-platform-injected-secrets.md) |
| OQ-05 | Frontend location | `frontend/` beside `backend/` | [ADR 0021](0021-frontend-beside-backend-in-one-repository.md) |
| OQ-06 | Environments documented | Local and a generic container deployment | [container deployment](../operations/deployment/container-deployment.md) |

## Deviations observed in the repository

| ID | Observation | Needed |
|---|---|---|
| OQ-10 | `spring-boot-admin-starter-server` in `pom.xml` is not in the project definition; owner says unintended | Remove it, or write an ADR if it becomes wanted |
| OQ-11 | `pom.xml` sets Java 17; [ADR 0019](0019-java-21-baseline.md) decides 21 | Update build, CI and images |
| OQ-12 | No JPA starter, although `ddl-auto=validate` is set | Add JPA in milestone 4 |
| OQ-13 | Tracked root `.env` (in history) and a hardcoded datasource password | Owner cleanup and credential rotation; see [secrets management](../operations/security/secrets-management.md) |
| OQ-14 | `adminer:latest` in `compose.yaml` is not version-pinned | Decide whether to pin |

## Undecided

| ID | Question | Blocks |
|---|---|---|
| OQ-20 | Frontend login flow (server-side session vs browser-held tokens) | [Milestone 6](../product/roadmap/milestone-6-frontend-integration.md), CORS and CSRF policy |
| OQ-21 | Package layout, location of ports, how a feature declares its public contract | [Milestone 1](../product/roadmap/milestone-1-architectural-skeleton.md) |
| OQ-22 | Whether the starter ships an example feature (needed to demonstrate feature-boundary rules) | [Architecture enforcement](../architecture/architecture-enforcement.md) |
| OQ-23 | How consuming applications adopt the starter and receive updates; what the starter guarantees them | [Context and boundaries](../architecture/context-and-boundaries.md) |
| OQ-24 | Baseline role names, if any | [Authorization](../architecture/identity/authorization.md) |
| OQ-25 | Behavior when Keycloak keys cannot be retrieved | [Token validation](../architecture/identity/token-validation.md) |
| OQ-26 | API error format, validation error shape, status mapping, pagination, versioning, CORS | [API conventions](../architecture/api/conventions.md) |
| OQ-27 | ID strategy, auditing fields, tenant-ready columns | [Persistence stack](../architecture/data/persistence-stack.md) |
| OQ-28 | Migration naming, policy on editing released migrations, rollback approach | [Schema migrations](../architecture/data/schema-migrations.md) |
| OQ-29 | CI platform and pipeline contents | [Verification standard](../engineering/testing/verification-standard.md) |
| OQ-30 | Branching and commit-message conventions; test naming and test data | [Engineering](../engineering/README.md) |
| OQ-31 | Next.js router, version and test tooling | [Milestone 6](../product/roadmap/milestone-6-frontend-integration.md) |
| OQ-32 | Container image build, base image, frontend deployment, migration timing, health contract | [Container deployment](../operations/deployment/container-deployment.md) |
| OQ-33 | Naming scheme and catalog of Kibootia configuration properties and environment variables | [Application configuration](../operations/configuration/application-configuration.md) |
| OQ-34 | Whether a threat model is written, and when | [Operations security](../operations/security/README.md) |
