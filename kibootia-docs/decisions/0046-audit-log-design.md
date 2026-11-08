# 0046 Audit log design

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Context

[ADR 0025](0025-identity-and-authorization-data-split.md) requires an audit log of every role, permission and assignment change, and the reference application shows it to administrators. The log is append-only and excluded from soft delete ([ADR 0034](0034-soft-delete-with-retention-purge.md)), while ADR 0034 also allows immediate erasure of a user for privacy requests. Personal data written into the log would survive that erasure. The `audit` module is used by every other module ([ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md)), and its table is hard to change once data accumulates.

## Decision

- **Scope:** every security and administrative event of the foundation (role and permission changes, role assignments, invitation, disabling, deletion, restore and erasure of users, the bootstrap-administrator grant, administrator-triggered purges, retries of failed events), plus a public API through which any module records its own business events. Sign-in events stay in Keycloak's event log and are not duplicated.
- **Entry contents:** who (actor user ID), what (a stable action code), the target (type and ID), when, tenant, trace ID, outcome, and changed values as field names with non-personal values (for example `roles: +editor`). People are referred to **only by user ID**; never names or email addresses, secrets or tokens.
- **Display:** the viewer resolves current names when showing entries. After a user is erased, entries show "deleted user" with the ID. Erasure therefore pseudonymises the log without rewriting it.
- **Retention:** configurable, default **365 days**, removed by the purge job ([ADR 0031](0031-transactional-outbox-and-locked-jobs.md)).
- **Write timing:** in the **same database transaction** as the change it describes, through the `audit` module's API. A change cannot exist without its entry, nor an entry without its change.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Full change history of every record (Envers-style) | Doubles write volume and holds a lot of personal data; kept as a recipe |
| Names and emails stored in entries | Erasure would have to rewrite the append-only log, or keep personal data |
| Keep entries forever by default | Unbounded growth; data minimisation |
| Write entries through the outbox after commit | A window in which a change exists without its entry |

## Consequences

- Every module that changes something auditable depends on the `audit` module's API.
- The audit viewer needs read access to profile names through the `identity` module's public contract.
- [ADR 0031](0031-transactional-outbox-and-locked-jobs.md) named audit entries as an outbox use; they are written synchronously instead.

## Status

accepted.

## Related

[Audit log](../architecture/audit-log.md), [threat model](../operations/security/threat-model.md).
