# 0003 Role administration by import and Super Admin

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

## Context

The documents say that users, roles and HOD assignments are "seeded manually" in version 1, that CR and BR have **no assign powers**, and that Super Admin "configures rooms, the slot grid, the calendar and notification settings" ([roles and powers](../modules/lecture-rescheduling/rules/permissions.md), [scope](../modules/lecture-rescheduling/overview/scope.md)). They do not say who creates or changes role assignments. The inherited application already ships administration screens for users, roles and the audit log.

## Decision

- **Users and their scoped role assignments are imported from files** by an idempotent import. Accounts are created through the existing identity provider port and invitation lifecycle.
- **Super Admin can adjust assignments** in the administration screens, which are extended to show and set the scope (department, division or batch).
- **HOD, CR and BR do not assign roles** in version 1. There is no delegation.
- The inherited Administrator role stays the technical owner of the platform. How the Administrator role relates to the Timetia Super Admin role is decided in the [open questions](../architecture/identity/open-questions.md) (OQ-A9).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Import or seed only, no assignment screens | Least code, but every change needs a developer or operator. |
| HODs manage CR and BR in their scope | Contradicts "no assign powers" and enlarges the authorization scope into delegation. |

## Consequences

- The Super Admin role gains user and role administration powers that the role files do not list today. [Super Admin](../shared/roles/super-admin.md) and the roles and powers file need an update once this is built. They are not edited by this decision.
- An import format, an import job, a provisioning runbook and the scope-aware screens become work packages in the [implementation plan](../architecture/identity/implementation-plan.md).
- Every import and assignment change is written to the audit log.

## Status

Accepted, 2026-11-11, by the project owner.
