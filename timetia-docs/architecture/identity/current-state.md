# Current state

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

What authentication and authorization Timetia has today. Everything here is **inherited from Kibootia, unchanged**: outside `timetia-docs`, the branch `ishan/authn-authz` has no difference from Kibootia's `main` at commit `4cea34f`. A diff of `backend`, `frontend`, `infra`, `compose.yaml`, `.github` and `scripts` between the two is empty.

How to read the labels. These facts come from reading the code and Kibootia's documents on 2026-11-11. **No test was run.** "Covered by" names a test that exists, and Kibootia's own documents record it as verified in their CI. That is Kibootia's claim, not a result of this audit.

Paths below are under `backend/src/main/java/com/kibootia/backend/` unless they start with another folder.

## What Timetia has changed since inheriting it (2026-11-12)

The classification below describes the inherited code as audited on 2026-11-11. Phases 0 to 2 of the [implementation plan](implementation-plan.md) have since changed it. The changes are in the repository with tests; none has a CI run yet.

| Area | Change | Where |
| --- | --- | --- |
| Role assignment | A role is given only by someone who holds every permission in it; an administrator may give any role (R1) | `access/application/RoleAssignmentService.requireGrantable` |
| Administrators | Only an administrator may disable, enable, delete, restore or erase an administrator (R2) | `identity/application/UserAdministration.requireMayActOn` |
| Tokens | Only access tokens: `typ` must be `Bearer` | `platform/security/UserTokenValidator` |
| Security events | A 403, a refused account and a rate-limit refusal are audited, throttled per user | `platform/security/SecurityEvents`, `audit/application/SecurityEventRecorder` |
| Production guard | `kibootia.environment=production` refuses development defaults | `platform/config/RequiredSettingsEnvironmentPostProcessor` |
| Identity configuration | Registration off; Timetia names for the realm, clients, audience, cookies and header | `infra/keycloak`, `compose.yaml`, `frontend/src/lib`, `application.properties` |
| Scope | A role has an optional scope type and an assignment a scope id | migration `V202611121000__access_scoped_assignments.sql`, `access/persistence/JpaRoleStore` |
| Policy-decided permissions | A permission that flat checks never satisfy and a record policy decides | `platform/authorization/Permission`, `ScopedResourcePolicy`, `ScopedGrants`, `Reach`; `access/domain/DefaultDecisionPoint`, `access/persistence/StoredScopedGrants` |
| Scoped assignment | `GET` and `PUT /api/v1/users/{id}/scoped-roles`, administrators only, audited | `access/application/ScopedAssignmentService`, `access/web/ScopedUserRolesController` |
| Role rules | A role with a policy-decided permission needs a scope type; a role with one holds only those; only an administrator puts one in a role | `access/application/RoleService` |
| Timetia roles and rules | The permission catalogue, the six role templates (seeded at startup) and the per-record policy | module `academic`: `AcademicPermissions`, `AcademicRoles`, `AcademicAccessPolicy`, `RoleTemplateSeeder` |
| Contract for other modules | Facts interfaces, `Authorization.reach`, `ScopeDirectory` | `academic/*AccessFacts`, `platform/authorization` |
| Provisioning import (Phase 3) | `POST /api/v1/user-imports?dryRun=`: a CSV of people and scoped roles, Super Admin only, add only, a person's rows together or not at all, bounded and audited | `identity/application/UserImportService`, `ImportCsv`, `UserImportProperties`; `identity/web/UserImportsController` |
| Scope codes (Phase 3) | `ScopeDirectory` gains `idOf(kind, code)` and `codesOf(kind, ids)`; a place can be named by code | `platform/authorization/ScopeDirectory`, `access/application/ScopedAssignmentService` |
| One assignment at a time (Phase 3) | `POST` adds and `DELETE …/{roleId}?scopeId=` removes one scoped role; the whole-set `PUT` stays | `access/web/ScopedUserRolesController`, `ScopedAssignmentService.addScopedRole`, `removeScopedRole` |
| Published port for the import (Phase 3) | `access.ScopedAssignments`: resolve a role name and a place code, add if missing. Administrators only; only roles with a scope type | `access/ScopedAssignments`, `ScopedAssignmentService` |
| First Super Admin (Phase 3) | At startup, with the bootstrap address set and no administrator, the application invites it; the person becomes administrator at their first sign-in | `identity/application/BootstrapAdministratorInvitation`, `ProfileGate` |
| `administrator` on `GET /me` (Phase 3) | A hint for showing administrator-only controls | `identity/application/ProfileService`, `identity/web/MeResponse` |
| Screens (Phase 3) | "Roles in a place" on the user page; an Import section | `frontend/src/features/access/ScopedRolesEditor.tsx`, `features/identity/ImportScreen.tsx` |
| Rate limit (Phase 3) | A new `imports` category, ten an hour | `platform/ratelimit/RateLimitCategory` |
| A scope directory that fails is a refusal (Phase 3 completion) | A `ScopeDirectory` that throws makes the place `access.scope_unvalidated` (409), never a 500 and never an accept; showing codes falls back to ids | `access/application/ScopedAssignmentService.place`, `codesOf` |
| Users cannot edit their email or username (Phase 3 completion) | Only an administrator may, in the realm's user profile. **Proposed, not confirmed by the owner** ([ADR 0007](../../decisions/0007-users-cannot-edit-their-own-email.md)) | `infra/keycloak/base/main.tf` |
| Accessibility fixes | Two light-theme colour tokens darkened; a keyboard-reachable scrolling table (`scrollLabel`); a heading on the note error page | `frontend/src/app/globals.css`, `components/ui/table.tsx`, `features/notes/NoteEditor.tsx` |
| Test support for other modules (Phase 4) | `AcademicApiTest` (roles in places in one line), `ScopeDirectoryContract` (what a scope directory must do), `AuthorizationWorkedExampleIT` (the model for a module) | `backend/src/test/.../academic`, `platform/fixtures` |

Still true of the application: no module provides departments, divisions and batches, so a role that needs a place cannot be assigned (it is refused) by the screen or by an import, and no module yet implements the facts interfaces. A person cannot see their own roles in a place (`GET /me` lists platform roles only), and nothing in the running application uses the permissions of the Timetia roles yet, so they are proven in integration tests and not through HTTP. See the [integration contract](integration-contract.md) and [OQ-A15](open-questions.md).

## Classification

### Implemented, covered by tests in Kibootia

| Capability | Where | Covered by |
| --- | --- | --- |
| Stateless API, every endpoint needs a token unless marked public, no CORS, CSRF or session | `platform/security/SecurityConfiguration` | `ResourceServerIT` |
| Token checks: signature, issuer, expiry, audience `kibootia-api`, service-account tokens refused | `SecurityConfiguration.jwtDecoder`, `AudienceValidator`, `UserTokenValidator` | `ResourceServerIT` |
| Identity resolution: the token `sub` is looked up in `user_profile`, created on first request after asking Keycloak, claims refreshed, disabled and deleted users refused | `identity/application/ProfileGate`, `platform/security/RequestGateFilter` | `IdentityGateIT` |
| Roles and permissions in the database, not in the token. Permissions are declared in code. Built-in `Administrator` role (implicitly every permission) and `User` role | `access/*`, migration `db/migration/access/V202611101000__access_create_role_tables.sql` | `AccessApiIT`, `DefaultDecisionPointTest`, `BuiltInRolesIT`, `TwoInstancesIT` |
| One decision point with `can` and `scopeFor`. Own versus all by permission name (`x` and `x-any`). Email-verified gate. System authority for jobs | `platform/authorization/*`, `access/domain/DefaultDecisionPoint` | `AuthorizationIT`, `DefaultDecisionPointTest` |
| Every public use case has a permission check or an explicit `@NoAuthorization("reason")` | `ArchitectureTest`, `AuthorizationRules` (rule 7) | architecture tests and fixture tests |
| User administration: invite, disable, delete, restore, erase, role assignment, last-administrator protection | `identity/*`, `access/*` | `UserAdministrationIT`, `AccessApiIT` |
| Editing a role can only add permissions the editor holds | `access/application/RoleService.requireGrantable` | `AccessApiIT` |
| Append-only audit log written in the caller's transaction, no personal data | `audit/*` | `AuditLogIT`, `AuditViewerIT` |
| Browser sign-in through the Next.js server: PKCE, encrypted HttpOnly cookie, CSRF check, header-stripping proxy | `frontend/src/lib/*`, `frontend/src/proxy.ts` | Vitest tests; Playwright tests |
| Per-user rate limits on administration and upload actions (per instance, in memory) | `platform/ratelimit/*` | `RateLimitIT` |

The Playwright end-to-end tests run only nightly, on manual start, or on pull requests labelled `e2e`. Kibootia's own status records that its 31 end-to-end tests were skipped in the latest pull request run.

### Partly implemented

- **Scope.** Only all, own or none, decided by permission naming. A per-record rule hook (`ResourcePolicy`) exists and nothing implements it. It cannot filter lists.
- **Audit.** Written for administration, roles, user changes, note deletion and restore, and the bootstrap administrator. **Not** written for refused requests, first sign-in, the default role grant, note create or update, avatar changes or viewing the audit log. `AuditEntry.failedWith` is never called by production code.
- **Tenancy.** The database layer isolates by tenant, but there is a single hard-wired default tenant, and jobs name it directly.
- **Production setup.** The Keycloak configuration has a local overlay only. There is no production overlay.

### Documented but not implemented

- A startup check that fails when development defaults are present ([token validation](../../../kibootia-docs/architecture/identity/token-validation.md)). No such code exists.
- A nonce-based content security policy for the Next.js server. No content security policy header is set today.

### Missing for Timetia

The source of department, division and batch data (the scope itself, the roles and the import exist; the places do not). The link between a user and a faculty or student record. Rights that depend on a ticket's relations (author, requested reviewer, affected faculty): the policy exists, nothing supplies the facts. A production Keycloak configuration. Per-role end-to-end tests.

## The request path today

1. The browser calls the Next.js server. The server holds an encrypted cookie session and forwards each API call with `Authorization: Bearer`. The browser never sees a token. (`frontend/src/app/api/[...path]/route.ts`, `frontend/src/lib/bff/proxy.ts`)
2. `apiSecurityFilterChain` in `platform/security/SecurityConfiguration` requires authentication on every request. A second chain exposes the health endpoints on a separate management port only.
3. The `JwtDecoder` fetches Keycloak's signing keys and applies three checks: the standard issuer and expiry check, `AudienceValidator` (the token must be for `kibootia-api`), and `UserTokenValidator` (refuses service-account tokens).
4. There is no custom converter. Spring's default builds the authentication. **Roles are never read from the token.**
5. `TenantContextFilter` binds the single default tenant. `RequestGateFilter` then calls `ProfileGate.admit`: it parses `sub` as a UUID, finds or creates the `user_profile`, refuses disabled and deleted users, refreshes name and email from the token and, at first sign-in, can grant the bootstrap administrator.
6. A use case is entered. `@RequiresPermission(...)` checks a permission. `Authorization.require(action, record)` checks a record. `Authorization.requireScope(action)` returns all, own or none for a list.
7. `DefaultDecisionPoint` reads the caller's permissions from the database on every call, with no cache, so a change applies on the next request. A first matching `ResourcePolicy` may decide a record. Otherwise own means the record's owner equals the caller.
8. A refusal becomes a Problem Details response: `platform.unauthenticated` (401), `platform.forbidden` or `platform.email_not_verified` (403), written by `platform/security/ProblemDetailsHandlers` (filter level) and `platform/web/GlobalExceptionHandler` (controller level). A record the caller may not view is answered 404 by convention.

## Components other modules touch

| Component | Role |
| --- | --- |
| `platform/authorization/Authorization` | `currentUserId()`, `can`, `require`, `scopeFor`, `requireScope`, `requireVerified`, `isEmailVerified()`, `runAsSystem` |
| `platform/authorization/RequiresPermission`, `NoAuthorization` | Annotations that every use case needs one of |
| `platform/authorization/PermissionProvider`, `Permission` | How a module declares its permissions |
| `platform/authorization/Owned`, `TenantBound`, `Scope`, `ResourcePolicy` | Record-level checks |
| `access/RoleAssignments`, `access/RoleSummary` | What `identity` uses to assign and read roles |
| `platform/directory/UserDirectory` | Read-only user lookups across modules |
| `audit/AuditLog`, `AuditEntry` | Write an audit entry in the caller's transaction |
| `platform/ratelimit/RateLimiter` | Per-user limits |

The data kept for identity and roles is: `user_profile` (keyed by the Keycloak `sub`), `role`, `role_permission`, `role_assignment` and `role_permission_seed`, and the audit table `audit_entry`. Role assignments carry no scope today.

## Where the design is explained

The reasoning and the safeguards are in Kibootia's decisions: [0006](../../../kibootia-docs/decisions/0006-keycloak-oidc-identity.md), [0025](../../../kibootia-docs/decisions/0025-identity-and-authorization-data-split.md), [0027](../../../kibootia-docs/decisions/0027-central-authorization-decision-point.md), [0041](../../../kibootia-docs/decisions/0041-user-lifecycle-and-account-model.md), [0044](../../../kibootia-docs/decisions/0044-authorization-enforcement-conventions.md), [0045](../../../kibootia-docs/decisions/0045-keycloak-operations.md) and [0046](../../../kibootia-docs/decisions/0046-audit-log-design.md).

Defects found in this code are in the [hardening backlog](hardening-backlog.md).
