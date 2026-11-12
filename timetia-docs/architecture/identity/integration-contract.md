# Integration contract

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

What a developer of another Timetia module may depend on for authentication and authorization, what that module must provide in return, and what is internal. The aim: a module can be built without reading the security code.

Everything under "Stable" is in the repository with tests (see [testing and verification](testing-and-verification.md) for how far each was run). Everything under "Not built yet" is not.

## Stable

### Who is calling, and protecting an operation

| You need to | Use | Rule |
| --- | --- | --- |
| Know who is calling | `Authorization.currentUserId()`, `isEmailVerified()` | Never read the token or the security context yourself. |
| Protect an operation that needs only a permission | `@RequiresPermission("module.resource.action")` on the use case method | Every public use case needs this or `@NoAuthorization("reason")`. The architecture test fails the build otherwise. |
| Declare a permission | A `@Component` implementing `PermissionProvider`, with names like `module.resource.action` | Names are checked at startup. An unknown name in `@RequiresPermission` stops the application. |
| Check one record | `authorization.require(action, record)` or `can(action, record)` | Load the record first. See "Policy-decided permissions" below for Timetia's permissions. |
| Filter a list by what a person may see | `authorization.requireScope(action)` for ordinary permissions; `authorization.reach(action)` for policy-decided ones | See below. |
| Hide or refuse | Answer **404** for a record the caller may not see. Answer **403** for a record the caller may see but may not change. | See [Kibootia ADR 0044](../../../kibootia-docs/decisions/0044-authorization-enforcement-conventions.md). |
| Record an audit entry | `AuditLog.record(AuditEntry.of("module.resource.verb", type, id)...)` inside your transaction | Ids only. No names, emails or secrets. |
| Limit a sensitive action | `rateLimiter.acquire(category)` after the authorization check | New categories are added in `platform`. |
| Fail | Throw `ApiException` with code `module.reason`, or let `AccessDeniedException` propagate | Responses are Problem Details with a `code` and a `traceId`. |

Failures the caller sees:

| Situation | Status | Code |
| --- | --- | --- |
| No valid token | 401 | `platform.unauthenticated` |
| Valid token, permission missing | 403 | `platform.forbidden` |
| Email not verified | 403 | `platform.email_not_verified` |
| Account disabled or deleted | 403 | `identity.account_disabled` |
| Record not visible to the caller | 404 | your module's not-found code |

A 403 of a signed-in user, a disabled-account refusal and a rate-limit refusal are also recorded as security events in the audit log (`security.access.denied`, `security.account.refused`, `security.rate-limit.exceeded`), with ids and permission names only, at most 20 per user per minute. A 401 is logged and not recorded. You write nothing for this.

### Policy-decided permissions (Timetia's permissions)

The permissions in the [role and scope model](role-and-scope-model.md) are held through a role assigned within a scope, and decided per record. They are different from ordinary permissions in one way you must know: **no permission-only check ever satisfies them.** `@RequiresPermission` on one stops the application at startup, `authorization.can("…")` without a record is false, and the built-in Administrator does not hold them. You can only ask about a record:

```java
authorization.require(AcademicPermissions.TICKETS_REVERT, ticketFacts);   // throws 403 when not allowed
boolean may = authorization.can(AcademicPermissions.TICKETS_VIEW, ticketFacts);
```

The record you pass must implement the facts interface for what you are asking about. The module that owns the record builds it, because only that module knows the people and the place:

| Interface (in `com.kibootia.backend.academic`) | For | Provides |
| --- | --- | --- |
| `LectureAccessFacts` | A lecture, an occurrence, a timetable entry | `tenantId`, `departmentId`, `divisionId`, **all** `batchIds`, `sessionType`, `teachingFacultyUserIds` |
| `TicketAccessFacts` | A ticket (extends the lecture facts) | `authorUserId`, `affectedFacultyUserIds`, `requestedReviewerUserIds`, `permanentChange` |
| `IssueAccessFacts` | An issue (extends the lecture facts) | `openerUserId`, `mentionedFacultyUserIds` |

Rules for the facts:

- People are **user ids**. The module resolves its own faculty and student records to user ids. There is no person directory.
- A theory lecture of a division lists **every batch of the division**, and a shared course lists the batches of every division that takes it. A student of a batch is assigned to the batch, so a lecture is visible to them only if it lists their batch.
- A missing value never grants anything. A record of another tenant is always refused.
- The policy decides **whether this person may do this to this record**. It does not know the state of the ticket. Whether the lecture has started, whether the ticket is final, whether approvals count for the current revision, whether a faculty member is absent, conflicts: those are yours. Ask the policy, then check your own rules.
- Pass the facts as they are now. Nothing is cached, so a role change applies on the next request.

**Lists.** For a policy-decided permission, `authorization.reach(action)` returns a `Reach`:

- `reach.everywhere()` is true for a global grant. Do not filter.
- Otherwise `reach.idsOf("DEPARTMENT")`, `idsOf("DIVISION")` and `idsOf("BATCH")` are the places where the caller holds the permission. Turn them into a condition on **your own columns** (`department_id in (...) or division_id in (...)`), and for batches on the table that lists a lecture's batches.
- `reach.isEmpty()` means the caller holds it nowhere: return nothing.
- Rights that come from a relation (my own tickets) are not in the reach. Add them from your own data: `or author_user_id = :me`. The permissions that are relation-based are listed in the model.
- Apply the session rule yourself for lists: a division grant gives theory lectures only and a batch grant lab and tutorial lectures only, for the permissions that say so in the model. For a single record the policy applies it for you.

### Tests

- Every endpoint is included automatically in the "401 without a token" sweep in `ResourceServerIT`.
- Helpers for administrators, users and role setup are in `AdminApiTest` and `GrantedPermissions`. A test that needs the Timetia roles calls `RoleTemplateSeeder.seed()`, and one that gives roles within scopes imports `ScopeFixtures` for a stand-in scope directory.
- For each protected use case write an allowed test and a denied test. For each list write a scope test. [Testing and verification](testing-and-verification.md).

## Not built yet

| Needed | Status |
| --- | --- |
| A **scope directory**: something that says a department, division or batch exists | Nobody provides it in the application. The port (`ScopeDirectory`) exists. **Until a module provides it, a role that needs a place cannot be assigned**: the assignment is refused with `access.scope_unvalidated`. Global roles work. The owner of department, division and batch data implements the port. |
| Facts for tickets, lectures and issues | The interfaces exist. The module that owns the records implements them when it exists. |
| Assignment from files, and screens that show scope | Phase 3. The assignment API exists: `GET` and `PUT /api/v1/users/{id}/scoped-roles`. |
| A person directory | Deferred. The facts carry user ids. |
| Expiry of a role at the end of a term | Not designed. See [OQ-A2](open-questions.md). |

## What to do now

1. Use `@RequiresPermission` and `Authorization.currentUserId()` as above, and declare your permissions in a `PermissionProvider`. Do not hard-code role names.
2. For Timetia's permissions, implement the facts for your records and call `authorization.require(action, facts)`. Write the denied tests with the cases the model forbids: another department, another division, another batch, a lab lecture for a CR, a theory lecture for a BR.
3. Keep state rules in your own service, not in a permission.
4. Put your own migrations in your module's folder and your own audit action names under your module prefix.
5. If a rule is not written in the model, it is an open question for the owner of this area ([open questions](open-questions.md)), not something to decide in your code. The policy denies what the documents do not say.

## Internal: do not depend on

- The classes in `access`, `identity` and `audit` other than the published ports: `RoleAssignments`, `RoleTemplates` and the audit and rate-limit interfaces named above. The module boundary check fails the build if you do.
- `Authorization.runAsSystem`. It gives total authority and is for jobs and event handlers. A user request never runs as the system.
- The token, the decision point class, the grants port and the permission and role tables directly.
- Native SQL without a `tenant_id` condition. The tenant filter does not apply to native SQL.

## Agreement needed with the security owner

A new permission name or module name. A new rate-limit category. A new scope kind. A need to read anything about the caller that this contract does not provide. A rule the model does not state.
