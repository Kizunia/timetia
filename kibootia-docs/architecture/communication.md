# Communication Between Parts

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

Decision: [ADR 0014](../decisions/0014-in-process-events-no-broker-initially.md).

## Rules

| Need | Mechanism |
|---|---|
| An immediate result from another feature | Call its public contract synchronously |
| A local reaction to something that happened | Spring in-process application events, where suitable |
| Durable delivery, independent consumers, distributed processing, scale | A message broker, **only when** requirements justify it |

## Cautions

- In-process events are not durable messaging. They give no delivery guarantee and no retries; do not rely on them for anything that must not be lost.
- If a database change and a published message must be coordinated reliably, consider the transactional outbox pattern when a broker is introduced.
- Kafka and RabbitMQ are not initial requirements.

## External systems

Reach them through intentional boundaries (ports and adapters). Application logic should not be coupled to a vendor SDK or protocol detail unless that coupling is the point of the integration.

## Related

[Layers and boundaries](layers-and-boundaries.md), [goals and non-goals](../product/goals-and-non-goals.md).
