# 0041 User lifecycle and account model

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** verified (CI run 42780048416, 2026-11-10). Invitation, self-registration, the default `user` role, email verification in the decision point, disable, delete, restore and erase are built; see [ADR 0050](0050-milestone-3-implementation-decisions.md) for the choices made while building.

## Context

[ADR 0025](0025-identity-and-authorization-data-split.md) splits identity (Keycloak) from authorization data (Kibootia). It left open how accounts are created, what a new user may do (OQ-24), who owns name and email, and how disabled users are stopped. The documentation also contradicted itself on who sends email: ADR 0025 uses Keycloak's email actions, while the architecture overview, [ADR 0024](0024-package-modules-with-infrastructure-toggles.md) and the secrets list assumed a backend mail module. With open sign-up, [ADR 0029](0029-s3-object-storage-contract.md)'s rule that avatars are visible to every signed-in user of the tenant would let any newly registered stranger browse the user directory.

## Decision

**Account creation.** Both paths are on by default in the reference application:

- **Invitation:** an administrator enters an email address and may pre-assign roles. The backend creates the Keycloak user through the Admin API port and asks Keycloak to email a set-password link.
- **Self-registration:** Keycloak's registration page is enabled.

**Default role.** A built-in `user` role is assigned automatically when the profile is created at first sign-in. Administrators may edit its permissions but cannot delete it. Actions on one's own profile and avatar are allowed to any active user by ownership rules and need no role.

**Profile data.** Keycloak is the source of truth for username, email and first and last name. `user_profile` keeps a read copy, refreshed at every sign-in and whenever an administrator edits a user through Kibootia. Users change their password and email in Keycloak's account console, linked from the profile page. Kibootia's own profile fields are application-level only (avatar, preferences). Edits made directly in the Keycloak admin console appear at that user's next sign-in.

**Email verification.** Required by default, but enforced in Kibootia's decision point from the token's `email_verified` claim, not by Keycloak's setting that blocks sign-in. An unverified user may sign in and may only view their own profile, resend the verification email and sign out; everything else is denied with the `email_not_verified` code ([ADR 0043](0043-http-api-conventions.md)). Operations can later be marked as allowed before verification.

**Profile visibility.** Seeing other users' profiles and avatars requires the permission `identity.profiles.view`, and never crosses tenants. The built-in `user` role does not have it; the administrator role and roles that administrators create may.

**Email.** Keycloak sends all identity mail (invitation, verification, password reset) using its own SMTP settings; a local mail catcher receives it. **The backend has no mail module.** Application notifications are future tier.

**Disabled and deleted users.** Disabling or deleting a user also ends that user's Keycloak sessions through the Admin API, and the backend refuses requests from a disabled or deleted profile immediately, through the per-request permission lookup ([ADR 0044](0044-authorization-enforcement-conventions.md)). Deletion, restore and erasure follow [ADR 0034](0034-soft-delete-with-retention-purge.md).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Invite-only, self-registration as a documented switch (the architect's recommendation) | The owner wants both on by default |
| No default role | Every new user needs administrator work, and self-registration would give users nothing |
| Kibootia's UI edits name and email and pushes them to Keycloak | Kibootia would build and secure its own email-change verification, duplicating Keycloak |
| Keycloak's login-blocking email verification | Cannot later allow selected operations to unverified users |
| Every signed-in user sees all users of the tenant | With open sign-up any stranger could browse the user directory |
| A backend mail module now | Infrastructure with no confirmed feature using it |

## Consequences

- Production deployments that keep self-registration on must also review bot protection and rate limits.
- The decision point needs the `email_verified` fact and a way to mark operations allowed before verification.
- The architecture overview, ADR 0024's examples and the secrets list no longer mention backend mail; the production SMTP question becomes a Keycloak setting.

## Status

accepted. Resolves OQ-24. Extends [ADR 0025](0025-identity-and-authorization-data-split.md) and [ADR 0029](0029-s3-object-storage-contract.md) (avatar visibility now also needs a permission), which stay accepted.

## Related

[User lifecycle](../architecture/identity/user-lifecycle.md), [authorization](../architecture/identity/authorization.md), [authentication](../architecture/identity/authentication.md).
