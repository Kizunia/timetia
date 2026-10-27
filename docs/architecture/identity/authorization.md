# Authorization

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

Decisions: [ADR 0009](../../decisions/0009-role-based-authorization-first.md) (accepted), [ADR 0010](../../decisions/0010-keycloak-realm-roles-as-role-source.md) (draft).

## Principles (owner decisions)

- Authorization decides whether an authenticated principal may perform a specific operation.
- It is enforced in the backend. Frontend visibility checks are for usability and never a security control.
- Start with role-based authorization and leave room for more contextual policies.
- A complete ABAC engine is not an initial requirement.

## Role source (provisional)

Roles come from Keycloak **realm roles**, found in the token's `realm_access.roles` claim and mapped by a documented converter to Spring Security authorities. Client roles and groups were considered. Baseline role names (for example whether the starter defines any) are undecided; see [open questions](../../decisions/open-questions.md).

## Extension path

Role checks first. Contextual policies (ownership, tenant membership) are added later by new ADR, preserving a path toward [tenant-aware](../data/transactions-and-data-ownership.md) access. Organization management is out of scope.

## Failure behavior

An authenticated caller who lacks permission receives an authorization failure, distinct from an authentication failure. Response shape: [API conventions](../api/conventions.md) (open).

## Testing

Every protected endpoint needs allowed and denied tests. None exist yet.
