# 0027 Central authorization decision point; OPA as a recipe

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** the decision point, `can` and `scopeFor` are verified (CI run 42605511477, 2026-11-09); the stored roles it now reads, and the email-verification rule, are verified (CI run 42780048416, 2026-11-10) ([ADR 0050](0050-milestone-3-implementation-decisions.md)).

> **Extended by [ADR 0044](0044-authorization-enforcement-conventions.md)**: checks on use cases, permission names, `scopeFor` for lists, no permission cache, machine clients rejected. This decision still holds.

## Context

[ADR 0009](0009-role-based-authorization-first.md) fixes role-based authorization first and leaves room for contextual policies. The owner wants a more ambitious foundation: role-based access control, resource-level checks, and a growth path to attribute-based and contextual rules, without forcing a policy engine on every application. Open Policy Agent (OPA) was considered.

## Decision

Each module defines its **permissions in code**. Every protected operation goes through **one central decision point**, `can(principal, action, resource)`, called from Spring Security method security. Policies are written in Java inside the modules and cover:

- permission held (RBAC);
- resource-level rules such as ownership;
- later, contextual and attribute-based rules, including tenant facts ([ADR 0028](0028-tenant-ready-schema.md)).

The decision point is a port, so an external engine can replace the Java policies. **OPA is delivered as a recipe** with a worked example and a test. No OPA code or Compose service ships. A recipe can later be promoted to an optional module.

The backend is authoritative. Frontend visibility is for usability only. Every protected operation needs allowed and denied tests.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| OPA adapter and Rego policies shipped, switched off | Two rule sets (Java and Rego) must be kept in sync |
| No pluggable seam | Drops the stated path to an external policy engine |
| OPA by default | Another service for every developer; an OPA outage denies everything; policy input data must be supplied on every check |

## Consequences

Adds a permission model and a decision point to the foundation. A complete attribute-based engine is still not an initial requirement, so ADR 0009 stays accepted; this ADR extends it.

## Status

accepted. Extends [ADR 0009](0009-role-based-authorization-first.md), which stays accepted. Extended by [ADR 0044](0044-authorization-enforcement-conventions.md).

## Related

[Authorization](../architecture/identity/authorization.md), [ADR 0025](0025-identity-and-authorization-data-split.md).
