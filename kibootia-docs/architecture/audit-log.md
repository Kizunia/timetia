# Audit Log

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** writing and purging entries are verified (CI run 42605511477, 2026-11-09). The viewer (`GET /api/v1/audit-entries` and the Audit log screen) and the entries of milestone 3 are verified (CI run 42780048416, 2026-11-10).

Decision: [ADR 0046](../decisions/0046-audit-log-design.md). Module: `audit` ([ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md)).

## Purpose

Record who did what to which record, so administrators can review security-relevant changes, without the log becoming a store of personal data.

## What is recorded

- Every security and administrative event of the foundation: role and permission changes, role assignments, invitation, disabling, deletion, restore and erasure of users, the bootstrap-administrator grant, administrator-triggered purges, retries of failed events.
- Business events that a module chooses to record through the `audit` module's public API.
- Not recorded here: sign-ins and other login events, which stay in Keycloak's event log.

## Entry contents

| Field | Notes |
|---|---|
| ID, timestamp, tenant | Standard columns ([persistence stack](data/persistence-stack.md)) |
| Actor | User ID only |
| Action | Stable code, for example `access.role.assigned` |
| Target | Type and ID |
| Changes | Field names with non-personal values, for example `roles: +editor` |
| Outcome | Success or the failure code |
| Trace ID | Links the entry to logs and traces |

Names, email addresses, secrets and tokens are never stored.

## Rules

- Written in the **same transaction** as the change, through the `audit` module's API.
- Append-only: the application has no update path; only the retention purge deletes.
- Excluded from soft delete ([ADR 0034](../decisions/0034-soft-delete-with-retention-purge.md)).
- Retention is configurable, default 365 days.
- The viewer shows current names by looking up user IDs through the `platform` user directory, which `identity` implements; erased users appear as "deleted user" with their ID, and work no user started appears as the system. It filters by actor, action prefix, kind of record, record and time, and pages newest first.
- Viewing the log requires a permission of the `audit` module.

## Testing

Tests show that each foundation event writes exactly one entry in the same transaction, that a rolled-back change leaves no entry, that no personal data is stored, that the purge respects retention, and that the viewer is permission-protected and shows erased users as erased (`AuditLogIT`, `AuditViewerIT`, `UserAdministrationIT`). Role names are limited to characters that cannot form an email address, and a changed field may not be called `name`.
