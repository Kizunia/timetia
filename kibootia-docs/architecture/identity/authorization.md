# Authorization

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

Decisions: [ADR 0009](../../decisions/0009-role-based-authorization-first.md), [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md).

## Principles (owner decisions)

- Authorization decides whether an authenticated principal may perform a specific operation on a specific resource.
- It is enforced in the backend. Frontend visibility checks are for usability and never a security control.
- A complete attribute-based engine is not an initial requirement, but the design leaves room for contextual rules.

## Model

- **Permissions** are defined in code by each module (for example reading or managing users).
- **Roles** are bundles of permissions stored in Kibootia's database. Administrators create and edit roles and assign them to users in the administration interface. Tokens carry identity only, so role changes apply immediately, through a cache that is invalidated when roles change (the invalidation approach is open).
- **Baseline roles:** a protected built-in administrator role. Whether a default user role exists is open ([open questions](../../decisions/open-questions.md)).
- **Safeguards:** the last administrator cannot be removed or demoted, and every change to roles, permissions and assignments is written to an audit log.
- **First administrator:** a demo administrator locally; in production a one-time setting grants the role to a named user on first sign-in, only while no administrator exists, and is audited.

## Decision point

Every protected operation asks the [policy decision point](../../shared/glossary/policy-decision-point.md): `can(principal, action, resource)`, invoked through Spring Security method security. Policies behind it live in the modules and cover:

1. permission held (role-based);
2. resource-level rules such as ownership;
3. later, contextual rules, including tenant facts ([ADR 0028](../../decisions/0028-tenant-ready-schema.md)).

The decision point is a port, so an external engine such as OPA can replace the Java policies. OPA is a recipe in the [technology catalogue](../../technology/README.md); nothing of it ships.

## Failure behavior

An authenticated caller who lacks permission receives an authorization failure, distinct from an authentication failure. Response shape: [API conventions](../api/conventions.md) (open).

## Testing

Every protected operation needs an allowed test and a denied test. None exist yet.
