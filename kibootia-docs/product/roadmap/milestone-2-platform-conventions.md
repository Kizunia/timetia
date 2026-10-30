# Milestone 2: Platform Conventions

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No JPA, migrations or API conventions exist.

## Scope (owner decision)

The conventions every module relies on, each proven by a test: persistence, tenancy, API behavior, events and jobs.

## Completion criteria (proposed, not yet approved)

- JPA is added and Hibernate does not create or alter the schema ([ADR 0012](../../decisions/0012-flyway-managed-schema-evolution.md)). Migrations use the naming and per-module locations in [schema migrations](../../architecture/data/schema-migrations.md), and a Testcontainers MySQL test applies all of them to an empty database ([ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md)).
- UUIDv7 identifiers ([ADR 0033](../../decisions/0033-uuidv7-identifiers.md)), audit columns and optimistic locking are in place, with tests.
- The tenant context and `@TenantId` enforcement work, and cross-tenant isolation tests pass ([ADR 0028](../../decisions/0028-tenant-ready-schema.md)).
- The soft-delete conventions, including filtering, uniqueness and purge, are implemented and tested ([ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md)).
- One global error shape, validation errors, pagination and the 409 conflict response are implemented and covered by API tests ([API conventions](../../architecture/api/conventions.md)). The error shape itself is an open question.
- The OpenAPI description is generated and the frontend client is generated from it.
- The outbox and lock-protected job conventions work: retry, idempotency, failed-event view, tenant and trace propagation ([ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md)).
- The mapper approach is decided ([ADR 0005](../../decisions/0005-hand-written-model-mappers.md), draft) and a reference example keeps entity, application model and API type apart.
- All of the above pass in CI.
