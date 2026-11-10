# 0050 Milestone 3 implementation decisions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** verified (CI run 42780048416, 2026-11-10, pull request #5): 81 fast and 202 integration backend tests, the frontend lint, type check, 144 unit tests and build, the image scan, the secret scan, and 14 end-to-end tests against the full Compose stack.

## Context

[Milestone 3](../product/roadmap/milestone-3-identity-and-access-administration.md) built what [ADR 0025](0025-identity-and-authorization-data-split.md), [0027](0027-central-authorization-decision-point.md), [0041](0041-user-lifecycle-and-account-model.md), [0044](0044-authorization-enforcement-conventions.md) and [0046](0046-audit-log-design.md) decide. Those decisions fix what the platform does but leave open how the modules fit together without cycles, how a change that spans MySQL and Keycloak is ordered, and several details that only became visible while building. Each choice below was forced by a test, a module rule or a real Keycloak that behaved differently from the documentation.

## Decision

**Ports in `platform` break the module cycles.** `identity` writes audit entries and `audit` needs names; `access` needs to know who can still sign in and `identity` assigns roles. Direct dependencies would form cycles that Spring Modulith rejects. So `platform.directory.UserDirectory` (a read-only lookup of name and state, implemented by `identity`) serves `audit` and `access`, and `platform.security.RequestGate` (implemented by `identity`) lets the platform run the profile check without depending on it. `access.RoleAssignments` is the public contract `identity` uses for the steps of the user lifecycle. This is the same device as the decision point in [ADR 0049](0049-milestone-2-implementation-decisions.md).

**Two indexed lookups per request, not one.** [ADR 0044](0044-authorization-enforcement-conventions.md) sketched one query that loads the profile status and the permissions. Those tables belong to two modules, so the request gate reads the profile (`identity`) and the decision point reads the roles (`access`), each through its own module. Role changes and disabling still apply on the next request on any instance; there is still no cache.

**The request gate is where a token becomes a known user.** It runs after the tenant is bound and before any controller. It creates the profile on first use (first confirming with Keycloak that the user exists, so an old token of a user who was just erased cannot create the profile again), gives the user the built-in `user` role, copies name and email from the token, and refuses a disabled or deleted user with `identity.account_disabled`. The copy is made only when the token's issue time is newer than the last copy and something differs, so a stale token cannot undo a newer value and an ordinary request writes nothing. Concurrent first requests create one profile: the insert fails on the key and the loser reads the winner's row.

**Keycloak changes are ordered database first, then a durable event.** Disabling, deleting, restoring and erasing change MySQL and write the audit entry in one transaction and publish an event; a durable handler brings Keycloak in line, with the retries of [ADR 0049](0049-milestone-2-implementation-decisions.md). The handler reconciles from the profile as it is when it runs (enabled only for a user who is neither disabled nor deleted, sessions ended otherwise), so a late or repeated delivery is harmless. Kibootia refuses the user at once; Keycloak may lag by seconds, or by the length of an outage. Invitation is the one exception because the Keycloak account must exist first: it creates the account, then Kibootia's rows and the event in one transaction, and removes the account again if that transaction fails.

**Erasure removes Keycloak first, then the rows.** An administrator may erase only a user who is already deleted. Erasure marks the profile, audits it and publishes `UserErased`; the identity handler deletes the Keycloak account, then the roles and the profile, then audits the erasure as the system, and other modules (`notes`) remove what they hold from the same event. Until the account is gone the profile still refuses the user. The retention job only begins erasure, exactly as the administrator's request does, and skips users whose erasure began.

**Two safeguards for administrators.** Nobody can disable or delete their own account through the API. The last administrator who can sign in cannot be removed, disabled or deleted: the use cases that could remove one run at `READ_COMMITTED`, lock the administrator role's row, and count the other administrators through `UserDirectory`, so two administrators removing each other at once leave exactly one. An invited user who has not accepted, a disabled user and a deleted user do not count as administrators who can sign in.

**The bootstrap administrator needs a verified address.** `kibootia.identity.bootstrap-admin-email` makes that address the first administrator, while no administrator exists, when the token carries it with `email_verified` true: at the user's first sign-in, or at the moment they first verify it if they registered before verifying. Registering first with someone else's address therefore captures nothing. The grant is audited as `identity.bootstrap-admin.granted`. Locally the setting names `demo-admin@kibootia.localhost`, a user the OpenTofu overlay creates, so there is no separate seed path.

**Email verification is part of every decision.** A caller who has not verified their address holds only the permissions marked `allowedBeforeVerification` (none, as shipped); the decision point returns `NONE` for the rest, and the denial is the coded `platform.email_not_verified` (403) instead of `platform.forbidden`. Viewing one's own profile, asking for the verification mail again and signing out need no permission and work before verification. `GET /me` returns no permissions to an unverified user. Because `email_verified` only changes when the token does, the web application has a CSRF-protected `POST /auth/refresh` that renews the session at once; the "I have verified" button uses it.

**Roles.** Permissions are defined in code and only their names are stored, so a permission a module no longer declares is ignored. The administrator role has no rows: it holds every declared permission, and cannot be edited or deleted. The `user` role's permissions are editable but it cannot be renamed or deleted. It is offered each permission a module flags for it once (a seed table records the offer), so an administrator's removals survive restarts and a permission added by a later release still reaches it. A role editor can only add permissions they hold; only an administrator can give the administrator role; a role that is still held cannot be deleted. Role names exclude `@` so they can never look like an email address in the audit log, and the audit field is `roleName` because the log refuses a field called `name`.

**Link rows are deleted, not soft-deleted.** `role_permission` and `role_assignment` are removed outright when a permission leaves a role or a role leaves a user; the audit log is their history. This is a recorded exception to [ADR 0034](0034-soft-delete-with-retention-purge.md), whose reasons (recoverability of business records, unique keys, personal data kept) do not apply to rows with no content of their own. Roles and user profiles stay soft-deleted, and a retention job purges deleted roles and starts erasing deleted users.

**The profile key comes from outside.** `user_profile` is keyed by the Keycloak user ID, so `BaseEntity` was split: `AuditedEntity` holds the tenant, audit columns and version, `BaseEntity` adds the generated key, `AssignedIdEntity` adds a given key and soft delete. A row with a given key is persisted with `EntityManager.persist`, because Spring Data cannot tell a new row from an old one by a key that is always set. Status, theme and the built-in marker are plain strings in the entity, so schema validation needs no native enum column type. Stores that use an `EntityManager` are `@Repository` beans so that a duplicate key arrives as `DataIntegrityViolationException`.

**The Keycloak adapter is a small `RestClient`, not the Keycloak admin client.** About ten endpoints do not justify a library that brings its own HTTP stack and JSON mapper into a Spring Boot 4 application. It keeps the service-account token until shortly before it expires, retries once on a 401, reads and rewrites the whole account to disable it (a partial update could clear attributes), and reports every failure as `Unavailable` with a message that never holds the secret, a token or a response body.

**Keycloak 26.8 needs the Admin API audience on the service account's token.** An Admin API call is refused with 401 unless the token's audience is the realm's admin API address, which the built-in `kc-admin-api` client scope adds. A client created by OpenTofu or a realm import does not have that scope until it is made a default scope. [infra/keycloak/base/main.tf](../../infra/keycloak/base/main.tf) now sets the default scopes of `kibootia-admin`. The adapter test against a real Keycloak and a mail catcher found this, which is why it exists; the same test shows a client with only the view roles is refused every change. The links in Keycloak's emails lead to a confirmation page ("Click here to proceed") before an action runs, and the web client's redirect URIs now include the application's front door, where those links return. Invited users are created with a verified address (following the link proves the mailbox) and with the required actions to set a password and a name.

**Rate limiting is Bucket4j in memory.** One token bucket per user and category, held in a size-bounded Caffeine cache whose entries expire once a bucket would have refilled, so memory stays bounded. Categories are invitations, administrative actions, verification mail and uploads, each with a default capacity and period that `kibootia.rate-limit.limits.<category>.*` overrides. The limiter is called in the use case after its authorization check, so an unauthorized caller uses up no one's allowance. The uploads category exists but is applied by [milestone 4](../product/roadmap/milestone-4-files.md), where uploads do. Bucket4j 8.16.1 is the newest line that has had a patch release ([ADR 0035](0035-version-adoption-policy.md)).

**Frontend.** The screens use shadcn/ui (copied into the repository), TanStack Query, React Hook Form with Zod, and the generated client. The shadcn stylesheet is copied too, rather than depending on the shadcn command line package and its 220 dependencies; components are added with `pnpm dlx shadcn@4.23.0 add <name>`. Two generated defaults were changed because tests showed a defect: the destructive button is a solid red with white text (the pale tint failed the contrast check), and a save that refreshes the data and so remounts its form is awaited instead of given callbacks, which TanStack Query drops for an unmounted component. Hiding what a user may not do is for usability only; every action is checked again by the API.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| `access` and `audit` depend on `identity` directly | Module cycles; Spring Modulith rejects them |
| One query for status and permissions | Needs one module to read another's tables |
| Keycloak first, inside the request | An outage would block disabling a user, and a crash between the two steps leaves them inconsistent |
| Keep a permission cache with a version number | Not needed at this size; the per-request queries are cheap and always right |
| Soft-delete role links | A row with no content of its own, and an extra filter on every request, for no recoverability the audit log does not give |
| Keycloak admin client library | A second HTTP stack and a Jackson 2 dependency for about ten calls |
| Resilience4j or a hand-written limiter | Built for call sites, not per-user keys; or security code we would own |
| Names and emails in audit entries | See [ADR 0046](0046-audit-log-design.md) |

## Consequences

- Every use case that can remove an administrator runs at `READ_COMMITTED` and calls `ensureNotLastAdministrator`; a new such use case must do the same.
- Each request makes two indexed queries and a profile refresh that writes only when the token is newer and something differs.
- A deployment that sets the bootstrap address and leaves it set costs nothing once an administrator exists, but should clear it.
- A Keycloak realm created outside OpenTofu must give the administration client the `kc-admin-api` default scope. Without it every call to the Admin API is refused, so administration and the first sign-in of each new user answer 503.
- The first sign-in with the bootstrap address, or the first verified one, decides the grant; adding the setting later to a user who already signed in verified does nothing.

## Status

accepted. Extends [ADR 0025](0025-identity-and-authorization-data-split.md), [0027](0027-central-authorization-decision-point.md), [0041](0041-user-lifecycle-and-account-model.md), [0044](0044-authorization-enforcement-conventions.md) and [0046](0046-audit-log-design.md), which stay accepted. Records an exception to [ADR 0034](0034-soft-delete-with-retention-purge.md), which stays accepted.

## Related

[User lifecycle](../architecture/identity/user-lifecycle.md), [authorization](../architecture/identity/authorization.md), [audit log](../architecture/audit-log.md), [frontend](../architecture/frontend.md), [threat model](../operations/security/threat-model.md).
