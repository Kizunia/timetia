# 0012 Flyway-managed schema evolution

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed (dependency only); no migrations exist.

## Context

Schema changes must be reviewable, repeatable and safe in production. Source: project definition, section 7.2.

## Decision

All schema changes are versioned Flyway migrations. Hibernate does not create or update the production schema. Migrations are reviewed and tested.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Hibernate auto DDL | Not reviewable or repeatable; unsafe in production |

## Consequences

Entity changes require a matching migration. Hibernate is limited to validation (`ddl-auto=validate` is already in `application.properties`).

## Status

accepted

## Related

[Schema migrations](../architecture/data/schema-migrations.md).
