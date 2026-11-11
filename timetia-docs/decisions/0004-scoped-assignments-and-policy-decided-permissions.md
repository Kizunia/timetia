# 0004 Scoped assignments and policy-decided permissions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

## Context

[ADR 0002](0002-scoped-roles-on-the-current-kibootia-contract.md) says Timetia adds a small scoped-role layer by extending Kibootia's contract. [ADR 0003](0003-role-administration-by-import-and-super-admin.md) says Super Admin adjusts assignments. This decision fixes the details that those two left open, so the work can be built: where the scope is stored, how a scoped right is decided, how it relates to the inherited Administrator role, and how a scope is validated.

The inherited decision point knows a flat set of permissions per user, and reduces them to all, own or none ([current state](../architecture/identity/current-state.md)). A right such as "an HOD may revert a change in their department" cannot be expressed that way, and a forgotten record check would let any holder of the permission act anywhere.

## Decision

1. **Scope is stored by extending the access tables** (option A in the [target architecture](../architecture/identity/target-architecture.md)). A role gets an optional scope type (`GLOBAL`, `DEPARTMENT`, `DIVISION`, `BATCH`; none for platform roles). A role assignment gets a scope id (all zeros means no scope), and its key becomes user, role and scope. The migration is additive and keeps every existing row valid.
2. **Policy-decided permissions.** A permission can be declared policy-decided. It is **never satisfied by the flat checks**: `@RequiresPermission` and the permission-only `can` and `scopeFor` deny it, and the built-in Administrator does not hold it implicitly. It can be used only by checking a record, where a module's per-record policy decides from the caller's grants and the record's facts. Forgetting the record check denies, and does not over-allow. An `@RequiresPermission` that names such a permission stops the application at startup.
3. **Invariants on roles.** A role that holds any policy-decided permission must have a scope type. A role that has a scope type holds only policy-decided permissions. Both are enforced when a role is created or edited.
4. **Grants are generic.** The platform knows a grant as a kind (a plain string) and an optional id. It does not know the words department, division or batch. The Timetia module declares those kinds.
5. **Super Admin is the built-in Administrator role.** It holds platform administration and the flat configuration permission, and it is the one role that can assign roles that carry policy-decided permissions. It does not hold the ticket permissions, which matches "not part of routine approval". The bootstrap administrator is the first Super Admin. [OQ-A9](../architecture/identity/open-questions.md) is answered.
6. **Scope validation is a port that fails closed.** A scope directory port says whether a department, division or batch exists. When no implementation is wired, assigning a role at a non-global scope is refused, so nothing is silently unvalidated. Security owns no department, division or batch tables ([OQ-A1](../architecture/identity/open-questions.md) is answered for Phases 0 to 2). The owner of that data implements the port.
7. **Facts use user ids.** The ticket module supplies the people on a ticket as user ids and the lecture's department, division and **all** batch ids. A person directory is deferred.
8. **Defaults taken for open questions**, each isolated so it can be changed with one rule and one test:
   - A person may hold several assignments. There are no expiry dates now ([OQ-A2](../architecture/identity/open-questions.md)).
   - An HOD's scope is the department of the lecture's division. Super HOD is global ([OQ-A3](../architecture/identity/open-questions.md)).
   - `Faculty` and `Student` are role templates that carry permissions. Relations come from the facts ([OQ-A4](../architecture/identity/open-questions.md)).
   - The author may revert their own ticket of any type before the lecture starts. An HOD may revert any change in the department. A permanent change is reverted only by an HOD or Super HOD ([OQ-A5](../architecture/identity/open-questions.md)).
   - Where the [role and scope model](../architecture/identity/role-and-scope-model.md) has a `?`, the right is **denied**.
   - A division-scoped grant applies to theory lectures only. A batch-scoped grant applies to lab and tutorial lectures only, for the actions where the documents give CR and BR that limit.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| A separate Timetia table of scoped assignments | Two sources of assignments. The old role endpoint could assign a Timetia role without a scope. The role-in-use and permission lookups would read both. |
| Treat scoped permissions as flat with a record policy narrowing them | A use case with only a permission check would be open to every holder in every scope. Denying by construction is safer. |
| Let the Administrator hold every permission, scoped ones too | Contradicts the documented limits of Super Admin: no authoring, no override, not in routine approval. |
| Security owns minimal department, division and batch tables | Security would own reference data the timetable also needs. The other developer's schema could conflict. |

## Consequences

- Kibootia's `access` module changes, additively. The change is recorded here, kept small, and can be offered to Kibootia.
- The `Permission` record, `Authorization`, the decision point and `ResourcePolicy` gain additive behavior. Existing signatures stay.
- Until the owner of department, division and batch data implements the scope directory port, scoped assignments cannot be created in a running system. Tests use a fake.
- The permission list that `GET /me` returns includes policy-decided permissions a person holds anywhere. It is a hint for the interface and not a decision.

## Status

Accepted, 2026-11-12, by the project owner, for the choices the owner made (1, 5, 6). The defaults in point 8 are the author's and are recorded for review.
