# ADR Index

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (index).

Every ADR in the project. Update this table in the same change that adds or alters an ADR. An accepted ADR is never rewritten; a changed decision is a new ADR that supersedes the old one. Rules: [documentation standards](../meta/documentation-standards.md#7-architecture-decision-records).

Accepted does not mean implemented: see [implementation status](../product/roadmap/implementation-status.md).

| ID | Title | Scope | Status | Date | Link |
|---|---|---|---|---|---|
| 0001 | Adopt STRUCTURE.md conventions selectively | platform | deprecated (superseded by 0022) | 2026-10-27 | [0001](0001-adopt-structure-conventions-selectively.md) |
| 0002 | Modular monolith as initial architecture | platform | accepted | 2026-10-27 | [0002](0002-modular-monolith.md) |
| 0003 | Hexagonal architecture where boundaries matter | platform | accepted | 2026-10-27 | [0003](0003-hexagonal-architecture-where-boundaries-matter.md) |
| 0004 | Separate API, application and persistence models | platform | accepted | 2026-10-27 | [0004](0004-separate-api-application-and-persistence-models.md) |
| 0005 | Hand-written model mappers | platform | draft | 2026-10-27 | [0005](0005-hand-written-model-mappers.md) |
| 0006 | Keycloak with OpenID Connect for identity | platform | accepted | 2026-10-27 | [0006](0006-keycloak-oidc-identity.md) |
| 0007 | Spring Security resource server with signature, issuer and expiry validation | platform | accepted | 2026-10-27 | [0007](0007-spring-security-resource-server.md) |
| 0008 | Audience validation and deny-by-default | platform | draft | 2026-10-27 | [0008](0008-audience-validation-and-deny-by-default.md) |
| 0009 | Role-based authorization first, extensible later | platform | accepted | 2026-10-27 | [0009](0009-role-based-authorization-first.md) |
| 0010 | Keycloak realm roles as role source | platform | draft | 2026-10-27 | [0010](0010-keycloak-realm-roles-as-role-source.md) |
| 0011 | MySQL, Spring Data JPA and Hibernate | platform | accepted | 2026-10-27 | [0011](0011-mysql-jpa-hibernate-persistence.md) |
| 0012 | Flyway-managed schema evolution | platform | accepted | 2026-10-27 | [0012](0012-flyway-managed-schema-evolution.md) |
| 0013 | ArchUnit architecture enforcement | platform | accepted | 2026-10-27 | [0013](0013-archunit-architecture-enforcement.md) |
| 0014 | In-process events now, no message broker initially | platform | accepted | 2026-10-27 | [0014](0014-in-process-events-no-broker-initially.md) |
| 0015 | Vault for production secrets | platform | accepted | 2026-10-27 | [0015](0015-vault-for-production-secrets.md) |
| 0016 | Platform-injected secrets (no Vault client in the application) | platform | draft | 2026-10-27 | [0016](0016-platform-injected-secrets.md) |
| 0017 | Testcontainers MySQL for persistence tests | platform | accepted | 2026-10-27 | [0017](0017-testcontainers-mysql-for-persistence-tests.md) |
| 0018 | CI run as the verification standard | platform | accepted | 2026-10-27 | [0018](0018-ci-as-verification-standard.md) |
| 0019 | Java 21 baseline | platform | accepted | 2026-10-27 | [0019](0019-java-21-baseline.md) |
| 0020 | Keycloak in local Compose with an imported realm | platform | accepted | 2026-10-27 | [0020](0020-keycloak-in-local-compose.md) |
| 0021 | Frontend beside backend in one repository | platform | draft | 2026-10-27 | [0021](0021-frontend-beside-backend-in-one-repository.md) |
| 0022 | Maintain Kibootia's own documentation standards | platform | accepted | 2026-10-27 | [0022](0022-maintain-own-documentation-standards.md) |

Module-scoped ADRs will be listed here too once modules exist.
