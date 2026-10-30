# Communication Between Parts

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

Decisions: [ADR 0014](../decisions/0014-in-process-events-no-broker-initially.md), [ADR 0031](../decisions/0031-transactional-outbox-and-locked-jobs.md).

## Rules

| Need | Mechanism |
|---|---|
| An immediate result from another feature | Call its public contract synchronously |
| A local reaction that may be lost without harm | Spring in-process application events |
| A reaction that must not be lost (cleanup, audit side effects, anything crossing a transaction) | A durable event written to the outbox table in the same transaction, delivered after commit with retries |
| Work that must run on a schedule, once across all instances | A lock-protected scheduled job |
| Independent consumers, distributed processing, scale | A message broker, **only when** requirements justify it; RabbitMQ is the documented recipe |

## Conventions for durable events and jobs

- Handlers are idempotent, using an event ID, because a delivery can repeat.
- Failures retry with backoff; events that keep failing appear in a failed-event view.
- Tenant context and trace context travel with the event or job.

## Cautions

- In-process events are not durable messaging. They give no delivery guarantee and no retries; do not rely on them for anything that must not be lost.
- The outbox gives at-least-once delivery inside the application, not exactly-once.
- Kafka and RabbitMQ are not initial requirements.

## External systems

Reach them through intentional boundaries (ports and adapters). Application logic should not be coupled to a vendor SDK or protocol detail unless that coupling is the point of the integration.

## Related

[Layers and boundaries](layers-and-boundaries.md), [goals and non-goals](../product/goals-and-non-goals.md).
