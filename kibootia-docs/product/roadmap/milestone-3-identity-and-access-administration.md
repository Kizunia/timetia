# Milestone 3: Identity and Access Administration

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Scope

The user-administration module and the authorization model: profiles, roles and permissions, the decision point in use, the admin interface, invitations, self-registration, the audit log viewer and rate limiting. Decisions: [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md), [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md), [ADR 0046](../../decisions/0046-audit-log-design.md).

## Completion criteria (proposed; rewritten on 2026-11-08 after the second decision round, awaiting owner approval)

- A `user_profile` row is created on first sign-in, keyed by the Keycloak user ID; name and email are refreshed from the token at every sign-in, and the built-in `user` role is assigned automatically ([user lifecycle](../../architecture/identity/user-lifecycle.md)).
- Permissions are defined in code, roles are editable, and every protected operation goes through the decision point. Each operation has an allowed and a denied test, and list endpoints test their scope.
- The protected admin role, the last-administrator safeguard and the first-administrator bootstrap work as specified, with tests.
- The admin UI manages users through the Keycloak Admin API port, using a least-privilege service account, and manages roles and assignments in Kibootia's database.
- Invitations and self-registration both work; Keycloak sends the mail and Mailpit receives it. An unverified user can only view their own profile, resend verification and sign out; other operations return `email_not_verified`.
- Disabling or deleting a user ends their Keycloak sessions, and the backend refuses their requests on the next request; role changes also apply on the next request, on two running instances.
- Seeing other users' profiles requires `identity.profiles.view`, which the built-in `user` role lacks.
- The audit log records every administrative change, an admin screen shows it, and a user erased by an administrator appears as "deleted user".
- The in-app rate limiter protects invitations, uploads and administrative actions.
- The threat model covers the new trust boundaries ([threat model](../../operations/security/threat-model.md)).
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).
