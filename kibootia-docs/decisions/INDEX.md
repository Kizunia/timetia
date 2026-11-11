# ADR Index

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** not applicable (index).

Every ADR in the project. Update this table in the same change that adds or alters an ADR. An accepted ADR is never rewritten; a changed decision is a new ADR that supersedes the old one. Rules: [documentation standards](../meta/documentation-standards.md#7-architecture-decision-records).

Accepted does not mean implemented: see [implementation status](../product/roadmap/implementation-status.md).

| ID | Title | Scope | Status | Date | Link |
|---|---|---|---|---|---|
| 0001 | Adopt STRUCTURE.md conventions selectively | platform | deprecated (superseded by 0022) | 2026-10-27 | [0001](0001-adopt-structure-conventions-selectively.md) |
| 0002 | Modular monolith as initial architecture | platform | accepted | 2026-10-27 | [0002](0002-modular-monolith.md) |
| 0003 | Hexagonal architecture where boundaries matter | platform | accepted | 2026-10-27 | [0003](0003-hexagonal-architecture-where-boundaries-matter.md) |
| 0004 | Separate API, application and persistence models | platform | accepted | 2026-10-27 | [0004](0004-separate-api-application-and-persistence-models.md) |
| 0005 | Hand-written model mappers | platform | accepted | 2026-10-27 | [0005](0005-hand-written-model-mappers.md) |
| 0006 | Keycloak with OpenID Connect for identity | platform | accepted | 2026-10-27 | [0006](0006-keycloak-oidc-identity.md) |
| 0007 | Spring Security resource server with signature, issuer and expiry validation | platform | accepted | 2026-10-27 | [0007](0007-spring-security-resource-server.md) |
| 0008 | Audience validation and deny-by-default | platform | accepted (tightened by 0047) | 2026-10-27 | [0008](0008-audience-validation-and-deny-by-default.md) |
| 0009 | Role-based authorization first, extensible later | platform | accepted (extended by 0027) | 2026-10-27 | [0009](0009-role-based-authorization-first.md) |
| 0010 | Keycloak realm roles as role source | platform | deprecated (superseded by 0025) | 2026-10-27 | [0010](0010-keycloak-realm-roles-as-role-source.md) |
| 0011 | MySQL, Spring Data JPA and Hibernate | platform | accepted | 2026-10-27 | [0011](0011-mysql-jpa-hibernate-persistence.md) |
| 0012 | Flyway-managed schema evolution | platform | accepted | 2026-10-27 | [0012](0012-flyway-managed-schema-evolution.md) |
| 0013 | ArchUnit architecture enforcement | platform | accepted (extended by 0039) | 2026-10-27 | [0013](0013-archunit-architecture-enforcement.md) |
| 0014 | In-process events now, no message broker initially | platform | accepted (extended by 0031) | 2026-10-27 | [0014](0014-in-process-events-no-broker-initially.md) |
| 0015 | Vault for production secrets | platform | accepted (partly superseded by 0032) | 2026-10-27 | [0015](0015-vault-for-production-secrets.md) |
| 0016 | Platform-injected secrets (no Vault client in the application) | platform | accepted | 2026-10-27 | [0016](0016-platform-injected-secrets.md) |
| 0017 | Testcontainers MySQL for persistence tests | platform | accepted | 2026-10-27 | [0017](0017-testcontainers-mysql-for-persistence-tests.md) |
| 0018 | CI run as the verification standard | platform | accepted | 2026-10-27 | [0018](0018-ci-as-verification-standard.md) |
| 0019 | Java 21 baseline | platform | deprecated (superseded by 0048) | 2026-10-27 | [0019](0019-java-21-baseline.md) |
| 0020 | Keycloak in local Compose with an imported realm | platform | accepted (partly superseded by 0045) | 2026-10-27 | [0020](0020-keycloak-in-local-compose.md) |
| 0021 | Frontend beside backend in one repository | platform | accepted | 2026-10-27 | [0021](0021-frontend-beside-backend-in-one-repository.md) |
| 0022 | Maintain Kibootia's own documentation standards | platform | accepted | 2026-10-27 | [0022](0022-maintain-own-documentation-standards.md) |
| 0023 | Template and fork adoption model | platform | accepted | 2026-10-28 | [0023](0023-template-fork-adoption-model.md) |
| 0024 | Package modules with infrastructure toggles | platform | accepted (extended by 0039) | 2026-10-28 | [0024](0024-package-modules-with-infrastructure-toggles.md) |
| 0025 | Identity in Keycloak, authorization data in Kibootia | platform | accepted (extended by 0041, 0044) | 2026-10-28 | [0025](0025-identity-and-authorization-data-split.md) |
| 0026 | Next.js as backend-for-frontend with an encrypted cookie session | platform | accepted (extended by 0042) | 2026-10-28 | [0026](0026-nextjs-bff-session.md) |
| 0027 | Central authorization decision point; OPA as a recipe | platform | accepted (extended by 0044) | 2026-10-28 | [0027](0027-central-authorization-decision-point.md) |
| 0028 | Tenant-ready schema | platform | accepted | 2026-10-28 | [0028](0028-tenant-ready-schema.md) |
| 0029 | S3 API object storage contract, RustFS locally | platform | accepted (extended by 0041, 0051) | 2026-10-28 | [0029](0029-s3-object-storage-contract.md) |
| 0030 | OTLP telemetry with a local Grafana LGTM container | platform | accepted | 2026-10-28 | [0030](0030-otlp-telemetry-and-local-lgtm.md) |
| 0031 | Transactional outbox and lock-protected jobs | platform | accepted (extended by 0039) | 2026-10-28 | [0031](0031-transactional-outbox-and-locked-jobs.md) |
| 0032 | Secrets from environment and files; Vault recommended | platform | accepted | 2026-10-28 | [0032](0032-secrets-from-environment-and-files.md) |
| 0033 | UUIDv7 identifiers | platform | accepted | 2026-10-28 | [0033](0033-uuidv7-identifiers.md) |
| 0034 | Soft delete with retention purge | platform | accepted | 2026-10-28 | [0034](0034-soft-delete-with-retention-purge.md) |
| 0035 | Version adoption policy | platform | accepted | 2026-10-28 | [0035](0035-version-adoption-policy.md) |
| 0036 | Technology catalogue and recipes as documentation areas | platform | accepted | 2026-10-28 | [0036](0036-technology-catalogue-and-recipes-areas.md) |
| 0037 | Single-server Compose reference deployment | platform | accepted | 2026-10-28 | [0037](0037-single-server-compose-reference-deployment.md) |
| 0038 | Frontend stack | platform | accepted | 2026-10-28 | [0038](0038-frontend-stack.md) |
| 0039 | Spring Modulith for module boundaries and the event outbox | platform | accepted | 2026-11-08 | [0039](0039-spring-modulith-module-boundaries-and-outbox.md) |
| 0040 | Local development topology and setup tooling | platform | accepted | 2026-11-08 | [0040](0040-local-development-topology-and-setup.md) |
| 0041 | User lifecycle and account model | platform | accepted | 2026-11-08 | [0041](0041-user-lifecycle-and-account-model.md) |
| 0042 | Generic backend-for-frontend API proxy and CSRF protection | platform | accepted | 2026-11-08 | [0042](0042-bff-api-proxy-and-csrf.md) |
| 0043 | HTTP API conventions | platform | accepted | 2026-11-08 | [0043](0043-http-api-conventions.md) |
| 0044 | Authorization enforcement conventions | platform | accepted | 2026-11-08 | [0044](0044-authorization-enforcement-conventions.md) |
| 0045 | Keycloak operations: PostgreSQL, realm configuration with OpenTofu, production exposure | platform | accepted | 2026-11-08 | [0045](0045-keycloak-operations.md) |
| 0046 | Audit log design | platform | accepted | 2026-11-08 | [0046](0046-audit-log-design.md) |
| 0047 | Health exposure and container images | platform | accepted | 2026-11-08 | [0047](0047-health-exposure-and-container-images.md) |
| 0048 | Java 25 baseline on Eclipse Temurin | platform | accepted | 2026-11-08 | [0048](0048-java-25-baseline-on-temurin.md) |
| 0049 | Milestone 2 implementation decisions | platform | accepted | 2026-11-09 | [0049](0049-milestone-2-implementation-decisions.md) |
| 0050 | Milestone 3 implementation decisions | platform | accepted | 2026-11-10 | [0050](0050-milestone-3-implementation-decisions.md) |
| 0051 | Milestone 4 implementation decisions | platform | accepted | 2026-11-10 | [0051](0051-milestone-4-implementation-decisions.md) |
| 0052 | Milestone 5 implementation decisions | platform | accepted | 2026-11-11 | [0052](0052-milestone-5-implementation-decisions.md) |

Module-scoped ADRs will be listed here too once modules exist.
