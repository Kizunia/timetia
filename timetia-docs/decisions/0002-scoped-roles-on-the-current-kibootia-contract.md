# 0002 Scoped roles on the current Kibootia contract

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

## Context

Timetia's rules need authorization that depends on where a person works and on how they relate to a ticket: an HOD acts for one department, a CR for one division's theory lectures, a BR for one batch's lab and tutorial lectures, a faculty member for lectures they teach. See [roles and powers](../modules/lecture-rescheduling/rules/permissions.md).

The authorization design Timetia inherited is flat. A user holds roles, a role is a set of permissions, and the only scope is all, own or none ([Kibootia ADR 0027](../../kibootia-docs/decisions/0027-central-authorization-decision-point.md), [ADR 0044](../../kibootia-docs/decisions/0044-authorization-enforcement-conventions.md)). It has an extension point for per-record rules that has no implementation yet.

Kibootia's author has a larger redesign for this, written as draft decisions, requirements and a milestone roadmap. At the time of writing it is **uncommitted, in one working tree, and not accepted**. Its Timetia scenarios were derived from a small part of Timetia's documents.

## Decision

- The baseline is the current Timetia branch, which carries Kibootia's committed code and documents.
- Kibootia's uncommitted authorization work is **acknowledged as context. It is not a constraint** on Timetia, and Timetia does not wait for it.
- Timetia adds a **small scoped-role layer by extending the existing contract**, not by replacing it. The layer is described in the [target architecture](../architecture/identity/target-architecture.md).
  - Role assignments gain a scope: global, department, division or batch.
  - A Timetia per-record policy decides rights that depend on a ticket's relations.
  - Lists can be filtered by the scopes the caller holds.
- Timetia's authorization code lives in its own module or modules. It does not edit the internals of Kibootia's `platform` and `access` modules beyond small, separable fixes that could be offered back to Kibootia.
- **Out of scope** unless a Timetia requirement appears: groups, delegation of roles, an attribute-based policy engine or external policy service, live multi-tenancy, permission caching, machine clients.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Wait for or implement Kibootia's redesign first, then consume it | Unaccepted, large, and mostly beyond Timetia's needs. Timetia's authorization work would wait on a decision it does not control. |
| Ignore Kibootia's contract entirely and build inside Timetia | Fastest to start, but the two repositories drift, and Kibootia's enforced conventions (checks on every use case, 404 versus 403, audit) would have to be rebuilt. |

## Consequences

- If Kibootia later accepts a different contract, Timetia may need a migration. Keeping Timetia code in its own module, and keeping it close to the shape of the current contract, limits that cost. Matching the draft is a nice-to-have, not a requirement.
- Defect fixes in Kibootia's code can be made in Timetia first. Each should be a small, self-contained change that can be offered to Kibootia later.
- The Kibootia rule that foundation code holds no application domain is respected: Timetia's department, division and batch knowledge arrives through ports, not through changes to `platform`.

## Status

Accepted, 2026-11-11, by the project owner.
