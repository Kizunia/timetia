# Schema Migrations

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-09

> **Implementation:** verified (CI run 42605511477, 2026-11-09). Migrations live in one folder per module (`platform`, `audit`, `notes`) and follow the naming below; `BackendApplicationIT` applies all of them to an empty MySQL and lets Hibernate validate the schema. Test-only tables live in `src/test/resources/db/migration/test-fixtures` and are never part of the application.

Decision: [ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md).

## Rules (owner decisions)

- Every schema change is a versioned Flyway migration.
- Production must not use Hibernate to create or update the schema.
- Migrations are reviewed and tested.

## Conventions (owner decisions of 2026-10-28)

- **Location:** each module has its own migration folder, so a module that is removed takes its migrations with it ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)).
- **Naming:** `V<yyyyMMddHHmm>__<module>_<description>.sql`. The timestamp avoids version collisions between applications that merge upstream releases.
- **Released migrations are never edited.**
- **Forward only:** undoing a change means writing a new migration.
- **Expand, then contract:** a destructive change is split across releases so the running application keeps working during a deployment.
- **When they run:** at startup locally. In the reference deployment a separate one-shot step runs them and must succeed before the application starts, which stays safe with several instances ([ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md)).
- **Seed data and reset:** demo data lives in a location loaded only in the local profile. A documented command wipes local volumes and seeds again. Production refuses to start with seed data enabled.

## Testing

A migration test applies all migrations to an empty MySQL started by Testcontainers and then lets the application validate the schema ([ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md)). `BackendApplicationIT` is that test; every integration test also starts from an empty database.
