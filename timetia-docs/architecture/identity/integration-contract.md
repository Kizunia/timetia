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

### The scope directory (for the owner of department, division and batch data)

The module that owns departments, divisions and batches provides one bean implementing `com.kibootia.backend.platform.authorization.ScopeDirectory`. Nothing else in the security code needs to change. It answers for the current tenant and for the kinds `DEPARTMENT`, `DIVISION` and `BATCH` (the constants in `AcademicScopes`):

| Method | Answers |
| --- | --- |
| `boolean exists(String kind, UUID id)` | Whether a live place of this kind has this id |
| `Optional<UUID> idOf(String kind, String code)` | The id of the live place of this kind with this code, or empty. The code is passed **as written in the file or on the screen**; the caller never trims or changes its case, so how codes are compared is your rule, applied the same way every time |
| `Map<UUID, String> codesOf(String kind, Collection<UUID> ids)` | The code of each of these places, for showing an assignment. An id with no live place is absent |

The rule you must keep: **within a tenant and a kind, a code names at most one live place.** A division called `A` that exists in several departments breaks it; a code such as `CSE-A` does not. The import and the screen name places by code, so people will write these codes in files. Agree the format with the security owner before the data is loaded. A deleted place must not be found by `idOf`, and an assignment to a place that is later deleted stays until an administrator removes it; it then matches no record, and the screen shows its id because `codesOf` has no code for it.

**What the callers do with your answers, so you know what you are held to:**

- A place is accepted only if `idOf` (for a code) and `exists` (for the id, always) both say yes. Anything else is refused as `unknown_scope`.
- **If your methods throw, the place is refused as `access.scope_unvalidated` (409).** Nothing is accepted, nothing is a 500, and a global role still works. The caller logs only the exception's class name, so do not put a code or an address in an exception message and expect it to be hidden elsewhere. Showing an assignment falls back to the id when `codesOf` throws.
- The caller never caches. Every assignment and every import row asks you.

**The contract as a test.** `ScopeDirectoryContract` (in `backend/src/test/java/com/kibootia/backend/platform/fixtures`) is an abstract JUnit class holding the rules above: live places exist and are found by code; unknown ones are not; kinds are separate; a deleted place is not found by id or code and has no code; a code freed by a deleted place can name a new place and then names only that one; a mixed list is answered correctly; odd questions (unknown kind, blank or null code, empty list) get an empty answer and not an exception; answers are stable; **a place of another tenant is invisible**. To prove your provider meets it, extend the class in your module's test, build your provider against a test database, and implement three hooks (`newDirectory`, `addPlace`, `deletePlace`) and `addPlaceInAnotherTenant`. `ScopeDirectoryContractTest` does this for the test double, so the double cannot pass tests that a real provider would fail. A provider that does not pass this class is not ready to be wired in.

The tests use `ScopeFixtures.FakeScopeDirectory` (`add(kind, id, code)`, `remove`, `setUnavailable`). `UserImportIT` and `ScopedAssignmentsApiIT` show what the callers do with it, including a directory that is down and a place that was deleted.

### A worked example

`AuthorizationWorkedExampleIT` (`backend/src/test/java/com/kibootia/backend/academic`) is a ticket service written the way your module would write its own, and it runs against the real decision point, the real roles and a real database. Copy its shape. `ExampleTickets` in that file is the model; the order matters:

1. **One record.** Load it. If it does not exist or `can(view…)` is false for the caller, throw not-found (404), so a forbidden record is not revealed. Then check the action: if the caller may see it and may not do this, throw `AccessDeniedException` (403). Then apply **your own** state rules (already reverted, final, the lecture has started). The policy decides who; it never knows the state.
2. **A list.** Never load everything and filter in memory, and never page before filtering. Call `authorization.reach(action)` and turn it into a condition on your columns: `everywhere` means no filter; department ids filter your department column; division ids filter your division column **and** `session_type = THEORY`; batch ids go through the table that lists a lecture's batches **and** `session_type in (LAB, TUTORIAL)`; an empty reach means return nothing without querying. Add rights that come from a relation (`author_user_id = :me`) **only if** the caller holds that permission somewhere (`!reach(…).isEmpty()`).
3. **Prove the list.** The example's test `listAgreesWithRecords` checks, for 11 people and 5 tickets, that the list contains exactly what the single-record decision allows. Write that test for every list you make; it is the only thing that catches a query that drifts from the policy. The theory-only rule in the query was removed on purpose and two tests failed.

What it does not show: an HTTP controller (map the two exceptions as the other modules do) and your real facts class (implement `TicketAccessFacts` on your own entity or a small adapter; the example uses the test record `Facts`).

### Tests

- Every endpoint is included automatically in the "401 without a token" sweep in `ResourceServerIT`.
- **To test a role in a place, extend `AcademicApiTest`** (same folder as the example). It seeds the six roles and a small campus (department A `CSE` holds division 1 `CSE-A` and batch 1 `CSE-A1`; department B `ME` holds division 2 and batch 2), and gives you one-line people and questions:

  ```java
  UUID hod = hodOf(Facts.DEPARTMENT_A);
  UUID cr = crOf(Facts.DIVISION_1);
  assertThat(can(hod, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, ticketInDepartmentA)).isTrue();
  assertThat(can(hod, AcademicPermissions.TICKETS_REVERT_IN_SCOPE, ticketInDepartmentB)).isFalse();
  assertThat(reachOf(cr, AcademicPermissions.TICKETS_VIEW_IN_SCOPE).idsOf("DIVISION")).containsExactly(Facts.DIVISION_1);
  unassign(hod, HOD, Facts.DEPARTMENT_A);   // the role is gone on the next question
  ```

  People are given roles through the real API, as the screen does, so everything enforced there is enforced in your test. The scope directory is a stand-in, because the application has none yet.
- Other helpers for administrators, users and role setup are in `AdminApiTest` and `GrantedPermissions`. A test that needs the Timetia roles without the base class calls `RoleTemplateSeeder.seed()`, and one that gives roles within scopes imports `ScopeFixtures` for a stand-in scope directory.
- For each protected use case write an allowed test and a denied test. For each list write a scope test. [Testing and verification](testing-and-verification.md).

## Not built yet

| Needed | Status |
| --- | --- |
| A **scope directory**: something that says a department, division or batch exists, and what its code is | Nobody provides it in the application. The port (`ScopeDirectory`) exists. **Until a module provides it, a role that needs a place cannot be assigned**, by the screen or by an import: the assignment is refused with `access.scope_unvalidated`. Global roles work. The owner of department, division and batch data implements the port. See "The scope directory" below ([OQ-A15](open-questions.md)). |
| Facts for tickets, lectures and issues | The interfaces exist. The module that owns the records implements them when it exists. |
| A person directory | Deferred. The facts carry user ids. |
| A way for a person to see their own roles in a place | Not built. `GET /me` lists platform roles and flat permissions only; a role in a place is shown to an administrator on the person's page and to nobody else. Checked in the browser on 2026-11-12: a person holding Faculty sees only `User` on their profile. A screen that needs it ("what can I do here") needs an endpoint first. |
| An owner for the scope directory | The owner is a teammate who has not been named in this repository. When named, record them in [OQ-A15](open-questions.md). |
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
