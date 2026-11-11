# Policy decision point

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (definition).

The single place in the backend that answers "may this caller perform this action on this resource?". Every protected operation asks it. Modules supply the policies behind it, and an external engine can replace them because it is a [port](port.md). Decision: [ADR 0027](../../decisions/0027-central-authorization-decision-point.md).
