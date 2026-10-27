# Milestone 3: Security Baseline

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned (dependencies only; see [implementation status](implementation-status.md)).

## Scope (owner decision)

Implement the security baseline: Keycloak with OpenID Connect, Spring Security token validation, and role-based authorization enforced in the backend. See [identity architecture](../../architecture/identity/README.md).

## Completion criteria (proposed, not yet approved)

- A local Keycloak with an imported development realm runs from Compose ([ADR 0020](../../decisions/0020-keycloak-in-local-compose.md)).
- The backend rejects requests with missing, expired, wrongly signed, wrong-issuer tokens (and, if [ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md) is accepted, wrong-audience tokens) with the documented failure behavior.
- Every endpoint requires authentication unless explicitly marked public.
- Realm roles map to authorities ([ADR 0010](../../decisions/0010-keycloak-realm-roles-as-role-source.md), draft) and a role-protected endpoint is tested for allowed and denied cases.
- These tests run and pass in CI.

## Open points

The frontend login flow is not part of this milestone's decisions; see [open questions](../../decisions/open-questions.md).
