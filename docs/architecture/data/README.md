# Data

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned (MySQL in Compose, driver and Flyway starters in pom; no JPA, no migrations).

| Item | Summary |
|---|---|
| [persistence-stack.md](persistence-stack.md) | MySQL, Spring Data JPA, Hibernate |
| [model-separation.md](model-separation.md) | API, application and persistence models and their mappers |
| [schema-migrations.md](schema-migrations.md) | Flyway-managed schema evolution |
| [transactions-and-data-ownership.md](transactions-and-data-ownership.md) | Transaction boundaries, feature data ownership, tenant path |

Related decisions: ADRs [0004](../../decisions/0004-separate-api-application-and-persistence-models.md), [0005](../../decisions/0005-hand-written-model-mappers.md), [0011](../../decisions/0011-mysql-jpa-hibernate-persistence.md), [0012](../../decisions/0012-flyway-managed-schema-evolution.md), [0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md).
