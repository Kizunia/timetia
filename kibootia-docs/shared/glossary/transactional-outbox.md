# Transactional outbox

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (definition).

A table in which an event is saved in the same database transaction as the business change, then delivered after commit with retries until it succeeds. It gives durable events without a message broker. Decision: [ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md).
