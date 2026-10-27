# Schema Migrations

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed (dependency only). Flyway starters and `flyway-mysql` are in `pom.xml`; there are no migration scripts.

Decision: [ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md).

## Rules (owner decisions)

- Every schema change is a versioned Flyway migration.
- Production must not use Hibernate to create or update the schema.
- Migrations are reviewed and tested.

## Testing

A migration test applies all migrations to an empty MySQL started by Testcontainers and then lets the application validate the schema ([ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md)). This is the intended design; no such test exists.

## Not yet decided

Migration file naming and location conventions beyond Flyway defaults, policy on editing a released migration, and rollback approach. Listed in [open questions](../../decisions/open-questions.md).
