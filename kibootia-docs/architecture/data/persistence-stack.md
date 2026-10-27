# Persistence Stack

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** partly observed: MySQL 8.4 runs from `compose.yaml` (host port 3307) and the MySQL driver is in `pom.xml`. Spring Data JPA/Hibernate is **not** a dependency yet.

Decision: [ADR 0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md).

## Stack (owner decision)

| Part | Role |
|---|---|
| MySQL | Initial relational database |
| Spring Data JPA | Repository abstractions and integration |
| Hibernate | JPA implementation |
| Flyway | Versioned migrations ([schema migrations](schema-migrations.md)) |

## Observations from the repository

- `application.properties` points at the local Compose database and sets `spring.jpa.hibernate.ddl-auto=validate`. Without a JPA dependency this property has no effect (inference); it will once JPA is added. `validate` is consistent with [Flyway-only schema evolution](schema-migrations.md).
- The datasource password is hardcoded in that file; see [secrets management](../../operations/security/secrets-management.md).

## Responsibilities and limits

JPA entities and repositories are infrastructure details owned by a feature and must stay behind its persistence boundary ([model separation](model-separation.md), [data ownership](transactions-and-data-ownership.md)).

## Open points

ID strategy, auditing fields (created/modified), and tenant-ready columns are undecided ([open questions](../../decisions/open-questions.md)).

## Testing

Persistence tests are to run against a real MySQL via Testcontainers ([ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md)). None exist yet.
