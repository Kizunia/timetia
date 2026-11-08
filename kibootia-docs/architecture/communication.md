# Communication Between Parts

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

Decisions: [ADR 0014](../decisions/0014-in-process-events-no-broker-initially.md), [ADR 0031](../decisions/0031-transactional-outbox-and-locked-jobs.md), [ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [ADR 0046](../decisions/0046-audit-log-design.md).

## Rules

| Need | Mechanism |
|---|---|
| An immediate result from another feature | Call its public contract synchronously |
| A local reaction that may be lost without harm | Spring in-process application events |
| A reaction that must not be lost (cleanup, anything crossing a transaction) | A durable event recorded by Spring Modulith's event publication registry (the outbox) in the same transaction, delivered after commit with retries |
| An audit entry | Written synchronously through the `audit` module's API in the same transaction as the change ([audit log](audit-log.md)) |
| Work that must run on a schedule, once across all instances | A lock-protected scheduled job |
| Independent consumers, distributed processing, scale | A message broker, **only when** requirements justify it; RabbitMQ is the documented recipe |

## Conventions for durable events and jobs

- Handlers are idempotent, using an event ID, because a delivery can repeat.
- Failures retry with backoff; events that keep failing appear in a failed-event view.
- Tenant context and trace context travel inside the event payload or job.
- The registry's table is created by a Flyway migration. If Spring Modulith fails its verification gate, a hand-written outbox table replaces the registry ([ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md)).

## Cautions

- In-process events are not durable messaging. They give no delivery guarantee and no retries; do not rely on them for anything that must not be lost.
- The outbox gives at-least-once delivery inside the application, not exactly-once.
- Kafka and RabbitMQ are not initial requirements.

## External systems

Reach them through intentional boundaries (ports and adapters). Application logic should not be coupled to a vendor SDK or protocol detail unless that coupling is the point of the integration.

## Related

[Layers and boundaries](layers-and-boundaries.md), [goals and non-goals](../product/goals-and-non-goals.md).
