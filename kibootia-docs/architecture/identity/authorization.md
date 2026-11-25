# Authorization

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** the decision point, annotations and scopes are verified (CI run 42605511477, 2026-11-09). Stored roles, the editable role model, email verification and the safeguards are verified (CI run 42780048416, 2026-11-10).

Decisions: [ADR 0009](../../decisions/0009-role-based-authorization-first.md), [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0027](../../decisions/0027-central-authorization-decision-point.md), [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md).

## Principles (owner decisions)

- Authorization decides whether an authenticated principal may perform a specific operation on a specific resource.
- It is enforced in the backend. Frontend visibility checks are for usability and never a security control.
- A complete attribute-based engine is not an initial requirement, but the design leaves room for contextual rules.

## Model

- **Permissions** are defined in code by each module, named `<module>.<resource>.<action>` (for example `identity.users.invite`, `identity.profiles.view`, `notes.notes.update-any`). They are registered at startup so the role editor can list them.
- **Roles** are bundles of permissions stored in Kibootia's database. Administrators create and edit roles and assign them in the administration interface. Tokens carry identity only.
- **Built-in roles:** a protected administrator role, and a `user` role assigned automatically at first sign-in. Administrators may change the `user` role's permissions but cannot delete it. In the reference application it does not include `identity.profiles.view`.
- **Ownership:** actions on one's own profile and avatar are allowed to any active user by ownership rules, without a role.
- **Email verification:** until the email address is verified, only viewing one's own profile, resending verification and signing out are allowed. Other operations are denied with `platform.email_not_verified`. A module can mark a permission `beforeVerification()`; none does yet. An unverified user holds no permissions, and `GET /me` says so.
- **Safeguards:** the last administrator who can sign in cannot be removed, demoted, disabled or deleted; a role editor can only give a role permissions they hold; only an administrator can give the administrator role; a role that is still held cannot be deleted; and every change to roles, permissions and assignments is written to the [audit log](../audit-log.md).
- **First administrator:** `kibootia.identity.bootstrap-admin-email` grants the role to the user whose token carries that address, verified, while no administrator exists, at their first sign-in or the moment they first verify it. It is audited. Locally it names the `demo-admin` user.

## Decision point

Every protected operation asks the [policy decision point](../../shared/glossary/policy-decision-point.md). It has two operations:

| Operation | Answers | Used for |
|---|---|---|
| `can(principal, action, resource)` | May this caller do this to this record? | Single records |
| `scopeFor(principal, action)` | Which records may this caller see or change: `ALL`, `OWN` or `NONE` (richer later)? | Lists; the module turns the scope into a query condition |

Policies behind it live in the modules and cover the permission held, record rules such as ownership, the email-verification fact, and later contextual rules including tenant facts ([ADR 0028](../../decisions/0028-tenant-ready-schema.md)). The decision point is a port, so an external engine such as OPA can replace the Java policies; OPA is a recipe in the [technology catalogue](../../technology/README.md).

## Where checks are written

On application use-case methods, not controllers, because use cases are also called by other modules, jobs and event handlers:

- `@RequiresPermission(...)` for permission-only checks;
- `authorization.require(action, record)` after loading a record, for record rules.

An ArchUnit rule requires every public use-case method to have a check or an explicit `@NoAuthorization("reason")` ([architecture enforcement](../architecture-enforcement.md)).

## Freshness

There is no permission cache at first. Each API request reads the caller's profile (the request gate, in `identity`) and each decision reads their permissions (`StoredPermissionSource`, in `access`), two indexed queries because each module owns its tables. Role changes and disabling therefore apply on the next request, on any number of instances; a test starts a second application context on the same database to show it. If measurement shows a need, the documented upgrade is an in-memory cache checked against a per-tenant authorization version number.

## Machine clients

Only tokens that belong to a user are accepted. Tokens issued to service accounts are rejected by default; service principals with roles are a future recipe.

## Failure behavior

An authenticated caller who lacks permission receives 403, distinct from 401 for authentication failures. A record the caller may not see at all is reported as 404 ([API conventions](../api/conventions.md)).

## Testing

Every protected operation has an allowed and a denied test, and list endpoints test their scope: `AccessApiIT` for roles and assignments, `UserAdministrationIT` and `IdentityGateIT` for users, `AuditViewerIT` for the log, `AuthorizationIT` and `DefaultDecisionPointTest` for the mechanism.
