# Authorization scope

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (definition).

The answer to "which records may this caller see or change?": `ALL`, `OWN` or `NONE`. A module turns it into a query condition so that lists are filtered in the database and paging stays correct. Decision: [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md); see also [policy decision point](policy-decision-point.md).
