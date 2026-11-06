# 0001 Standalone in version 1

- **Type:** adr
- **Module:** lecture-rescheduling
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-06

## Context

The original design assumed the ERP owns the base timetable and users. EduPlusCampus will not help in any way: no API, no webhooks.

## Decision

Version 1 is standalone. The module owns its base timetable, which is arranged and seeded manually. ERP sync is ignored for now. A webhook or similar integration comes later.

## Alternatives considered

Keep the ERP-owned framing as the version 1 design, with manual seeding as a stand-in. Rejected because it describes a system that cannot be built yet.

## Consequences

ERP-owned wording in the original spec is superseded. Data seeding becomes part of the work. See [ERP ownership](../integration/erp-ownership.md).

## Status

Accepted, 2026-11-06.
