# Integration contract

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

What a developer of another Timetia module may depend on for authentication and authorization, what arrives later, and what is internal. The aim: a module can be built without reading the security code.

Everything under "Stable now" exists in the inherited code and is described in [current state](current-state.md). It was read, not run. Everything under "Arrives in Phase 2" is **planned** ([implementation plan](implementation-plan.md)) and does not exist yet.

## Stable now

| You need to | Use | Rule |
| --- | --- | --- |
| Know who is calling | `Authorization.currentUserId()`, `isEmailVerified()` | Never read the token or the security context yourself. |
| Protect an operation | `@RequiresPermission("module.resource.action")` on the use case method | Every public use case needs this or `@NoAuthorization("reason")`. The architecture test fails the build otherwise. The reason is for a reviewer, so write one. |
| Declare a permission | A `@Component` implementing `PermissionProvider`, with names like `module.resource.action` | Names are checked at startup. An unknown name in `@RequiresPermission` stops the application. |
| Check one record | `authorization.require(action, record)` or `can(...)`, with the record implementing `Owned` and `TenantBound` | Load the record first. |
| Filter a list | `authorization.requireScope(action)`, then apply all or own to the query | The scope filter in Phase 2 adds department, division and batch. |
| Hide or refuse | Answer **404** for a record the caller may not see. Answer **403** for a record the caller may see but may not change. | See [Kibootia's API conventions](../../../kibootia-docs/decisions/0044-authorization-enforcement-conventions.md). |
| Record an audit entry | `AuditLog.record(AuditEntry.of("module.resource.verb", type, id)...)` inside your transaction | Ids only. No names, emails or secrets. |
| Limit a sensitive action | `rateLimiter.acquire(category)` after the authorization check | New categories are added in `platform`. |
| Read basic user data of other users | `UserDirectory` | Read only. |
| Fail | Throw `ApiException` with code `module.reason`, or let `AccessDeniedException` propagate | Responses are Problem Details with a `code` and a `traceId`. |

Failures the caller sees:

| Situation | Status | Code |
| --- | --- | --- |
| No valid token | 401 | `platform.unauthenticated` |
| Valid token, permission missing | 403 | `platform.forbidden` |
| Email not verified | 403 | `platform.email_not_verified` |
| Account disabled or deleted | 403 | `identity.account_disabled` |
| Record not visible to the caller | 404 | your module's not-found code |

### Tests

- Every endpoint is included automatically in the "401 without a token" sweep in `ResourceServerIT`. A new endpoint needs no extra work for that.
- Test helpers for administrators, users and role setup are in `AdminApiTest` and `GrantedPermissions` (under the backend test sources).
- For each protected use case write an allowed test and a denied test. For each list write a scope test. [Testing and verification](testing-and-verification.md).

## Arrives in Phase 2 (planned, not built)

| You need to | Planned interface | Notes |
| --- | --- | --- |
| Filter a list by department, division or batch | A scope filter from `Authorization` that returns "everything" or a set of scope ids with descendants | Added without changing `scopeFor`. |
| Decide rights that depend on a ticket | Implement the **ticket access facts** port for your module | Facts only: author, affected faculty, requested reviewers, department, division and batches, session type. See [role and scope model](role-and-scope-model.md). |
| Resolve a person | The **person directory** port, implemented by whoever owns faculty and student records | Maps a user to a faculty or student record. |
| Resolve a scope | The **scope directory** port, implemented by the owner of department, division and batch data | Existence, parents and children. |

The **ports are defined by security code and implemented by module owners.** Until the owner of a port has an implementation, tests use a fake and the integration is agreed in the work package (W2.5 to W2.7).

## What to do before Phase 2 lands

1. Use `@RequiresPermission` and `Authorization.currentUserId()` as in the table above, and declare your permissions in a `PermissionProvider`. Do not hard-code role names.
2. Keep state rules (has the lecture started, is the ticket final) in your own service, not in a permission.
3. Keep department, division, batch, session type and the people on a ticket as plain fields on your records, so the facts port can read them later.
4. Put your own migrations in your module's folder and your own audit action names under your module prefix.
5. Do not worry about who may see what beyond the documented rules. If a rule is not written, it is an open question for the owner of this area ([open-questions.md](open-questions.md)), not something to decide in your code.

## Internal: do not depend on

- The classes in `access`, `identity` and `audit` other than the published ports named above. The module boundary check fails the build if you do.
- `Authorization.runAsSystem`. It gives total authority and is for jobs and event handlers. A user request never runs as the system.
- The token, the decision point class, the permission tables and the role tables directly.
- Native SQL without a `tenant_id` condition. The tenant filter does not apply to native SQL.

## Agreement needed with the security owner

A new permission name or module name. A new rate-limit category. A new port implementation. A need to read anything about the caller that the table above does not provide.
