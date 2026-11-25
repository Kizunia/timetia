# 0034 Soft delete with retention purge

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Context

The default delete behavior had to be set. The architect recommended hard delete with an audit log and opt-in soft delete. The owner chose soft delete everywhere, after being shown its risks: unique keys that quietly misbehave, queries that must always filter, children that stay active under a deleted parent, personal data kept indefinitely, and files kept in storage.

## Decision

- **Scope:** all business and domain tables, in the foundation, the example feature and application modules. Technical and append-only tables are excluded: the audit log, the outbox and failed events, job locks and Flyway history.
- **Filtering:** Hibernate's soft-delete support filters entity queries; native queries need explicit filtering and tests. The options available in the Hibernate version in use must be verified.
- **Uniqueness:** unique keys include a generated active-marker column, because MySQL treats NULLs as distinct.
- **Children:** the application service soft-deletes child records explicitly.
- **Restore:** administrators can restore within the retention period, and restores are audited.
- **Purge:** a scheduled job ([ADR 0031](0031-transactional-outbox-and-locked-jobs.md)) permanently removes rows and their stored objects after a configurable retention period (default 30 days). An administrator can trigger immediate erasure for privacy requests.
- **Users:** a deleted user is disabled in Keycloak; final erasure deletes the Keycloak account at purge ([ADR 0025](0025-identity-and-authorization-data-split.md)).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Hard delete with an audit log (the architect's recommendation) | The owner prefers recoverability |
| Opt-in soft delete per module | As above |
| Soft delete with no purge | Personal data retained indefinitely, and storage keeps growing |

## Consequences

Every module applies the same conventions, and tests must cover the filters. Retention and erasure become configurable behavior with documentation obligations.

## Status

accepted.

## Related

[Persistence stack](../architecture/data/persistence-stack.md), [ADR 0033](0033-uuidv7-identifiers.md).
