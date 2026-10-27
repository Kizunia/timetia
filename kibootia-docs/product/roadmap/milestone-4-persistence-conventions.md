# Milestone 4: Persistence Conventions

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned (MySQL driver, Flyway starters and a Compose MySQL exist; no JPA, no migrations).

## Scope (owner decision)

Establish persistence conventions with MySQL, Spring Data JPA, Hibernate and Flyway, with separate application and persistence models. See [data architecture](../../architecture/data/README.md).

## Completion criteria (proposed, not yet approved)

- JPA is added to the build and Hibernate does not create or alter the schema ([ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md)).
- A first Flyway migration exists and applies to an empty database in a [Testcontainers MySQL](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md) test.
- A reference persistence example shows entity, application model and API DTO kept apart with [hand-written mappers](../../decisions/0005-hand-written-model-mappers.md) (draft).
- Transaction boundary guidance is written in [transactions and data ownership](../../architecture/data/transactions-and-data-ownership.md).
- ID strategy, auditing fields and tenant-readiness are decided or recorded as open.
- The tests pass in CI.
