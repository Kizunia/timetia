# 0014 In-process events now, no message broker initially

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

Features need to interact; most interactions at this stage are local. Source: project definition, section 8.

## Decision

Use public contracts for synchronous needs and Spring in-process events for suitable local reactions. In-process events are not durable messaging. Kafka and RabbitMQ are not initial requirements. Introduce a broker, and consider the transactional outbox, only when requirements justify it.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Broker from the start | Operational cost with no current requirement |

## Consequences

Nothing needing guaranteed delivery may rely on in-process events.

## Status

accepted

## Related

[Communication](../architecture/communication.md).
