# Problem details

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (definition).

The standard JSON error shape of RFC 9457. Kibootia adds a stable `code`, the request's `traceId` and, for validation failures, an `errors` list. Decision: [ADR 0043](../../decisions/0043-http-api-conventions.md); shape: [API conventions](../../architecture/api/conventions.md).
