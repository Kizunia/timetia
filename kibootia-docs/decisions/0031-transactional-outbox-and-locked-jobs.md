# 0031 Transactional outbox and lock-protected jobs

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

> **Extended by [ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md)**: the outbox is Spring Modulith's event publication registry (hand-written table as fallback). Audit entries are written in the same transaction rather than through the outbox ([ADR 0046](0046-audit-log-design.md)). This decision still holds.

## Context

[ADR 0014](0014-in-process-events-no-broker-initially.md) uses in-process events and no message broker, and says nothing needing guaranteed delivery may rely on them. Decisions taken since create exactly such needs: cleaning up a user's avatar and profile ([ADR 0025](0025-identity-and-authorization-data-split.md), [ADR 0034](0034-soft-delete-with-retention-purge.md)), writing audit entries for role changes, a scheduled orphan-file job that two application instances must not run at once ([ADR 0029](0029-s3-object-storage-contract.md)), and the example feature's events.

## Decision

- Domain events that must not be lost use a **transactional outbox in MySQL**: the event row is written in the same transaction as the business change and delivered after commit, with retries. The implementation is open: Spring Modulith's event publication registry (compatibility with the Spring Boot version in use must be verified) or a hand-written outbox table.
- **Scheduled jobs** take a database lock so that only one application instance runs them. The library is chosen at implementation time.
- Conventions for handlers and jobs: idempotency through an event ID, retry with backoff, a view of events that keep failing, and propagation of tenant context ([ADR 0028](0028-tenant-ready-schema.md)) and trace context ([ADR 0030](0030-otlp-telemetry-and-local-lgtm.md)).
- **No message broker.** RabbitMQ is the documented recipe when independent consumers or distributed processing become necessary; it fits typical application work queues better than Kafka, which suits high-volume streaming and replay.
- Plain in-process events remain suitable for reactions that may be lost.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Keep ADR 0014 unchanged | Cleanup and audit side effects can be lost if the application stops partway |
| Optional RabbitMQ module shipped off | A broker to maintain and test even while switched off |
| Broker by default | Every developer runs a broker without a concrete need |

## Consequences

One more table and the job conventions; no new infrastructure. Handlers must tolerate being run twice.

## Status

accepted. Extends [ADR 0014](0014-in-process-events-no-broker-initially.md), which stays accepted because "no broker initially" still holds. Implementation choice made in [ADR 0039](0039-spring-modulith-module-boundaries-and-outbox.md).

## Related

[Communication](../architecture/communication.md).
