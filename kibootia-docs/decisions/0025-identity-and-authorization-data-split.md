# 0025 Identity in Keycloak, authorization data in Kibootia

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** verified (CI run 42780048416, 2026-11-10). Built in milestone 3: the profile table, stored roles, the Admin API port and its adapter, the protected administrator role, the last-administrator safeguard and the bootstrap administrator ([ADR 0050](0050-milestone-3-implementation-decisions.md)). Keycloak and the realm are from milestone 1.

> **Extended by [ADR 0041](0041-user-lifecycle-and-account-model.md)** (account creation, default `user` role, profile data, email verification, disabled users) and [ADR 0044](0044-authorization-enforcement-conventions.md) (permission freshness without a cache). This decision still holds.

## Context

Kibootia includes a user-administration module ([goals and non-goals](../product/goals-and-non-goals.md)). [ADR 0006](0006-keycloak-oidc-identity.md) makes Keycloak the identity provider, and draft [ADR 0010](0010-keycloak-realm-roles-as-role-source.md) provisionally took roles from the token. The owner wants authorization to grow beyond roles (resource-level rules, tenant membership), and wants role changes to take effect immediately. Authorization data of that kind has no natural home in an identity provider's token.

## Decision

**Keycloak owns** credentials, login, token issuance and the user lifecycle (create, disable, reset, required actions). Kibootia does not store passwords and is not an identity provider.

**Kibootia's database owns** a `user_profile` row keyed by the Keycloak user ID (`sub`), created on first sign-in; role definitions and assignments; the role-to-permission mapping; and later resource grants and tenant memberships.

Tokens prove identity only. The backend resolves a caller's permissions per request, from a cache that is invalidated when roles change. The frontend reads the caller's permissions from a `/me` endpoint for usability only.

The user-administration module drives the user lifecycle through the **Keycloak Admin API behind an application-owned port**, using a dedicated confidential service-account client that holds only the realm-management roles it needs and never realm-admin.

**Roles are editable at runtime.** Permissions are defined in code by each module. Administrators create and edit roles (bundles of permissions) and assign them in the UI. A built-in admin role is protected, the last administrator cannot be removed, and every role, permission and assignment change is written to an audit log.

**First administrator.** Locally, the imported realm contains a demo administrator and local seed data assigns the admin role. In production, a one-time setting (`KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL`) grants the admin role to that user on first sign-in, only while no administrator exists, and the grant is audited.

Inviting a user uses Keycloak's email actions, which needs SMTP configuration; locally a mail catcher in Compose receives the mail.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Roles in Keycloak, carried in the token (the draft ADR 0010 approach) | Role changes wait for token refresh; fine-grained permissions and tenant data have no home and bloat tokens |
| Kibootia roles only, no Admin API | No privileged Keycloak credential, but administrators would need two consoles and access to Keycloak's own console |

## Consequences

- The backend now holds a client secret for administrative calls; the earlier statement that it needs none no longer holds for the user-administration module (see [authentication](../architecture/identity/authentication.md)).
- A permission lookup per request (cached) and a cache invalidation approach are needed; the approach is open.
- Keycloak can be replaced behind the administration port, though the sign-in flow still assumes OpenID Connect.
- Disable-versus-delete behavior follows [ADR 0034](0034-soft-delete-with-retention-purge.md).

## Status

accepted. Supersedes [ADR 0010](0010-keycloak-realm-roles-as-role-source.md) (draft). Resolves OQ-03; resolves part of OQ-24 (the baseline roles are the built-in admin role and a default user role, to be confirmed with the reference application). The default user role was confirmed in [ADR 0041](0041-user-lifecycle-and-account-model.md), which resolves the rest of OQ-24.

## Related

[Authorization](../architecture/identity/authorization.md), [authentication](../architecture/identity/authentication.md), [ADR 0027](0027-central-authorization-decision-point.md), [ADR 0028](0028-tenant-ready-schema.md).
