# Milestone 3: Identity and Access Administration

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** built; every criterion is covered by tests that passed in CI (run 42780048416, 2026-11-10, pull request #5): 81 fast and 202 integration backend tests, 144 frontend unit tests and 14 end-to-end tests against the full stack. Decisions made while building: [ADR 0050](../../decisions/0050-milestone-3-implementation-decisions.md).

## Scope

The user-administration module and the authorization model: profiles, roles and permissions, the decision point in use, the admin interface, invitations, self-registration, the audit log viewer and rate limiting. Decisions: [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md), [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md), [ADR 0046](../../decisions/0046-audit-log-design.md).

## Completion criteria (approved by the owner on 2026-11-08 with the other milestones, OQ-49)

- A `user_profile` row is created on first sign-in, keyed by the Keycloak user ID; name and email are refreshed from the token at every sign-in, and the built-in `user` role is assigned automatically ([user lifecycle](../../architecture/identity/user-lifecycle.md)).
- Permissions are defined in code, roles are editable, and every protected operation goes through the decision point. Each operation has an allowed and a denied test, and list endpoints test their scope.
- The protected admin role, the last-administrator safeguard and the first-administrator bootstrap work as specified, with tests.
- The admin UI manages users through the Keycloak Admin API port, using a least-privilege service account, and manages roles and assignments in Kibootia's database.
- Invitations and self-registration both work; Keycloak sends the mail and Mailpit receives it. An unverified user can only view their own profile, resend verification and sign out; other operations return `email_not_verified`.
- Disabling or deleting a user ends their Keycloak sessions, and the backend refuses their requests on the next request; role changes also apply on the next request, on two running instances.
- Seeing other users' profiles requires `identity.profiles.view`, which the built-in `user` role lacks.
- The audit log records every administrative change, an admin screen shows it, and a user erased by an administrator appears as "deleted user".
- The in-app rate limiter protects invitations and administrative actions, and the verification mail. The limiter also has an `uploads` category, which [milestone 4](milestone-4-files.md) applies, because uploads do not exist yet; the original wording named uploads here.
- The threat model covers the new trust boundaries ([threat model](../../operations/security/threat-model.md)).
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).

## Status against the criteria

| Criterion | Evidence |
|---|---|
| Profile on first sign-in, refreshed from the token, `user` role | `IdentityGateIT` (creation, concurrent first requests, refresh by token age, Keycloak deciding who is new); `administration` end-to-end story |
| Permissions in code, editable roles, every operation through the decision point, allowed and denied tests, scope tests | `AccessApiIT` (every roles endpoint allowed and denied), `UserAdministrationIT`, `AuditViewerIT`, `AuthorizationIT`, `DefaultDecisionPointTest`, ArchUnit rule 7 |
| Protected administrator role, last-administrator safeguard, bootstrap | `AccessApiIT` (including five rounds of two administrators removing each other at once), `UserAdministrationIT`, `IdentityGateIT` (bootstrap needs a verified address, once, audited) |
| Admin UI manages users through the Keycloak port with a least-privilege account; roles and assignments in Kibootia's database | `KeycloakAdminAdapterIT` (real Keycloak; a client with fewer roles is refused), `UserAdministrationIT`, `administration` end-to-end story, `role-editor`, `user-actions` and `invite-dialog` unit tests |
| Invitations and self-registration; Keycloak mails, Mailpit receives; unverified user limited | `KeycloakAdminAdapterIT` (both emails received), `administration` and `registration` end-to-end stories, `IdentityGateIT`, `AuthorizationIT` |
| Disable and delete end sessions and refuse on the next request; role changes on the next request on two instances | `UserAdministrationIT` (including an outage and repeated delivery), `TwoInstancesIT`, `AccessApiIT`, `administration` story |
| `identity.profiles.view` not in the user role | `IdentityGateIT`, `AccessApiIT`, `access-denied` end-to-end story |
| Audit log of every administrative change, a screen, erased users as "deleted user" | `AuditViewerIT`, `UserAdministrationIT`, `administration` story, `audit-screen` unit tests |
| Rate limiter on invitations and administrative actions | `RateLimiterTest`, `RateLimitIT` (429 with `Retry-After`, per user and per category) |
| Threat model entries | [threat model](../../operations/security/threat-model.md) |
| All of the above in CI | Pull request #5, run 42780048416 |

## Deviations and limits

- Uploads are limited from milestone 4 (see the criterion above).
- The failed-event view stays an API without a screen.
- An administrator cannot erase a user who is not deleted, and cannot act on their own account; both are additions to the specification, recorded in the ADR.
- Name and email are changed in Keycloak's account console, linked from the profile (ADR 0041); Kibootia keeps only the colour scheme as a preference.
- The first administrator is decided at sign-in; adding the bootstrap setting later to a user who already signed in verified grants nothing.
- Keycloak 26.8 needs the `kc-admin-api` default scope on the administration client; a realm built another way must add it ([authentication](../../architecture/identity/authentication.md)).
