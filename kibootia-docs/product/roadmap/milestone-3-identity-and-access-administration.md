# Milestone 3: Identity and Access Administration

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Scope (owner decision, [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md))

The user-administration module and the authorization model: profiles, roles and permissions, the decision point, the admin interface, invitations, the audit log and rate limiting.

## Completion criteria (proposed, not yet approved)

- A `user_profile` row is created on first sign-in, keyed by the Keycloak user ID.
- Permissions are defined in code, roles are editable, and every protected operation goes through the decision point. Each operation has an allowed and a denied test.
- The protected admin role, the last-administrator safeguard and the first-administrator bootstrap work as specified, with tests.
- The admin UI manages users through the Keycloak Admin API port, using a least-privilege service account, and manages roles and assignments in Kibootia's database.
- Invitations send a Keycloak email; a local mail catcher receives it.
- The audit log records every administrative change, and an admin screen shows it.
- The permission cache is invalidated when roles change (an open question), and the behavior is tested.
- The in-app rate limiter protects invites, uploads and admin actions.
- The threat model covers the new trust boundaries ([threat model](../../operations/security/threat-model.md)).
- All of the above pass in CI.
