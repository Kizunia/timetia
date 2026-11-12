# 0006 Provisioning and the first Super Admin

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

## Context

[ADR 0003](0003-role-administration-by-import-and-super-admin.md) says users and their scoped roles are imported from files and that Super Admin adjusts assignments in the administration screens. [ADR 0004](0004-scoped-assignments-and-policy-decided-permissions.md) built the scoped assignment and left a scope directory port that nothing implements. [ADR 0005](0005-identity-hardening-decisions.md) turned self-registration off, so nobody can arrive except by invitation or import, and nobody can invite before an administrator exists. This decision fixes the details Phase 3 needed: what the import file is, what an import may and may not do, how the first Super Admin comes to exist, and how the screens avoid undoing an import.

## Decision

**Chosen by the owner**

1. **Students are imported like everyone else.** Every new person, a student included, gets an account and an invitation email. The owner confirmed that sending thousands of emails is acceptable and that every student's address will be in the data. This answers [OQ-A10](../architecture/identity/open-questions.md). The limits in point 9 bound a single file; a large intake is several files.
2. **An import only adds.** It creates missing accounts and adds missing assignments. It never removes, changes, disables, enables, restores or re-invites anything, so running the same file twice changes nothing and a wrong file cannot silently take a role away. Removals are made by a Super Admin in the screen, one at a time.
3. **Places are named by code.** A row names a department, division or batch by the short code people use (`CSE`, `CSE-A`), not by an internal id. `ScopeDirectory` gains `idOf(kind, code)` and `codesOf(kind, ids)`, still failing closed: with no implementation no place can be checked, so no role that needs a place is given.
4. **The first Super Admin is invited by the application.** At startup, when `kibootia.identity.bootstrap-admin-email` is set and nobody holds the administrator role, the application creates that account in the identity provider and sends the invitation like any other. The person becomes administrator at their first sign-in with the verified address, only while no administrator exists, and that grant is audited.

**Taken by the author, for the owner to review**

5. **Only the Super Admin runs an import.** The caller must hold the built-in Administrator role, whatever other permissions they have, as for assigning a scoped role ([ADR 0004](0004-scoped-assignments-and-policy-decided-permissions.md), point 5). An import is a way to give a scoped role, so it cannot be a way around that rule.
6. **A file can only name roles that have a scope type.** The Administrator role, the `User` role and custom platform roles are refused (`role_not_assignable`), so no file can make anyone an administrator. Every row passes the same checks as the screen: the same code serves both.
7. **A person's rows go together or not at all.** If any row of a person has an error, none of their rows is applied, so no account is created from half a mistake. Other people are unaffected. A repeated line is skipped, not an error. A person who is disabled or deleted, an address with more than one account, and an address the identity provider has without a profile here are reported and left alone.
8. **Failures leave nothing behind.** A new person's account, profile, roles and audit entries are one transaction; if it fails the identity provider's account is removed again. Each person is their own transaction, so a failure affects only them and what came before stays.
9. **An import is bounded.** At most 1000 rows and 512 KB per file (413 beyond), a two-minute budget after which the rest is reported `NOT_PROCESSED`, a stop after five people in a row fail because the identity provider is down, ten real imports an hour (a new `imports` rate-limit category; dry runs use the general administration limit), and one real import at a time per tenant across instances (a database lock). Because a repeat only adds, the answer to every limit is to send the file again.
10. **A dry run is the same code, rolled back.** For people who already have an account a dry run runs the real assignment inside a transaction and rolls it back, so its answer is exact. It does not contact the identity provider, so it cannot know an address the identity provider already has; that surfaces only in a real run.
11. **The screen changes one assignment at a time.** `POST /api/v1/users/{id}/scoped-roles` adds one and `DELETE …/{roleId}?scopeId=` removes one. The existing `PUT` that replaces the whole set stays for scripts, but a screen opened before an import would use it to delete what the import added. The new endpoints touch only the assignment they name.
12. **The import sits inside `identity`, not a new module.** It needs to create accounts, which only `identity` may do. A separate module would have needed an exported way to create accounts for any caller. It uses one new published port from `access` (`ScopedAssignments`, resolve a name and add if missing), which is for administrators only and cannot reach a platform role.
13. **A bootstrap invitation that was not accepted is sent again at the next start.** If the person has not signed in and there is still no administrator, a restart sends the link again (audited). Otherwise an expired link would leave an environment nobody can invite from, and fixing it would take database work. Once an administrator exists nothing is sent, whatever the setting says.
14. **The report is data for the administrator only.** It contains the addresses from the file. It is returned to the caller and never logged or written to the audit log, which gets one summary entry per real run with counts and an id, plus the entries the invitations and assignments already write.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Sync: make each person's roles exactly what the file says | A wrong file silently removes roles. Term changeover is easier, but the owner chose the safer add-only behaviour. |
| Rows carry internal ids | Nobody preparing a file knows them, and they differ per environment. |
| Let a custom role with the invite and assign permissions run imports | Assigning a scoped role is an administrator-only act (ADR 0004); an import must not be a way around it. |
| A configuration-driven scope directory for local use | It would invent department, division and batch data. The owner of that data implements the port. |
| Create the first Super Admin by hand in the identity provider's console | Works today but is manual work in a system the application can already drive, and easy to get wrong (the address must be marked verified). |
| Keep the screen on `PUT`, with a version check | Larger change to a contract tests already pin, and still loses a race between two administrators. |

## Consequences

- **Roles that need a place still cannot be given in a running application** (HOD, CR, BR, Student) until the owner of department, division and batch data implements `ScopeDirectory`, including `idOf` and `codesOf`. Super HOD and Faculty import today. The import reports `scope_unvalidated` for the rest and invites nobody for them. This blocks the Phase 3 gate.
- **The data owner must keep one rule:** within a tenant and a kind, a code names at most one live place, and codes are compared the same way every time. The import passes the code as written and never normalises it. Division codes such as `A` that repeat across departments must be made unique (`CSE-A`) in the data.
- A new person's invitation email goes out after their transaction commits, through the existing durable event, so a slow or failing identity provider delays an email and does not lose it.
- If the identity provider's account cannot be removed after a failed row, that address stays known to it without a profile here, and later imports report `account_without_profile` for it until an operator resolves it in the identity provider (see the [runbook](../operations/provisioning.md)).
- Local environments are unchanged: the seeded `demo-admin` already exists in the identity provider, so the startup invitation does nothing and that account becomes administrator at its first sign-in as before.
- The `administrator` field on `GET /me` is a hint for showing controls. The backend decides every request.

## Status

Accepted, 2026-11-12. Points 1 to 4 were chosen by the owner in answer to direct questions. Points 5 to 14 are the author's and are recorded for review; each is one rule and one test.
