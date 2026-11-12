# Open questions

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

Decisions about authentication and authorization that the documents and the code do not settle. Each open question has a recommendation, which is a proposal and not a decision. Questions about ticket business rules that are not identity or access are in the [module's open questions](../../modules/lecture-rescheduling/open-questions.md).

## Decided

| Question | Answer | Where |
| --- | --- | --- |
| How do users sign in? | Keycloak, as Kibootia; no local passwords | [ADR 0001](../../decisions/0001-authentication-follows-kibootia.md) |
| How does Timetia relate to Kibootia's uncommitted authorization drafts? | Context, not a constraint | [ADR 0002](../../decisions/0002-scoped-roles-on-the-current-kibootia-contract.md) |
| Who administers roles? | Import and Super Admin; no delegation | [ADR 0003](../../decisions/0003-role-administration-by-import-and-super-admin.md) |
| OQ-A14 Where is scope stored? | In the access tables (a scope type on the role, a scope id on the assignment) | [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md) |
| OQ-A9 How does Administrator relate to Super Admin? | Super Admin is the built-in Administrator role | [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md) |
| OQ-A1 Who owns department, division and batch data? | Not security. A scope directory port, failing closed until implemented | [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md) |
| OQ-A8 Self-registration | Off in the Timetia realm | [ADR 0005](../../decisions/0005-identity-hardening-decisions.md) |
| OQ-A13 How far does the rename go? | Identity-facing names only | [ADR 0005](../../decisions/0005-identity-hardening-decisions.md) |
| OQ-A10 How do students get accounts? | Like everyone else: an account and an invitation email each, from the email in the data. The owner confirmed thousands of emails are acceptable and that every student's address will be in the data | [ADR 0006](../../decisions/0006-provisioning-and-bootstrap.md) |
| What does a repeated import do to roles a person already has? | Nothing: an import only adds. Removals are made one at a time in the screen | [ADR 0006](../../decisions/0006-provisioning-and-bootstrap.md) |
| How does a file name a department, division or batch? | By its code. The scope directory port looks codes up and still fails closed | [ADR 0006](../../decisions/0006-provisioning-and-bootstrap.md) |
| How does a fresh environment get its first Super Admin? | The application invites the configured address at startup while no administrator exists; they become Super Admin at first sign-in | [ADR 0006](../../decisions/0006-provisioning-and-bootstrap.md) |

### Defaults taken, for the owner to confirm or change

These were not chosen by the owner. They are the author's defaults, recorded in [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md), point 8. Each is one rule and one test.

| Question | Default |
| --- | --- |
| OQ-A2 Several roles and scopes per person, and expiry | Several assignments allowed. No expiry dates now. Add an end date if CR and BR roles are to expire at term end. |
| OQ-A3 Who decides a cross-department case | An HOD's scope is the department of the lecture's division. Super HOD is global. |
| OQ-A4 Are Faculty and Student roles | Role templates that carry permissions. Relations come from the facts. |
| OQ-A5 Who may revert a change that is not an exchange or a substitute | The author, any type except permanent. An HOD or Super HOD, any change in scope. A permanent change only by an HOD or Super HOD. |

### Defaults taken in Phase 3, for the owner to confirm or change

Recorded in [ADR 0006](../../decisions/0006-provisioning-and-bootstrap.md), points 5 to 14. Each is one rule and one test.

| Question | Default |
| --- | --- |
| Who may run an import? | Only the Super Admin (the built-in Administrator), whatever other permissions a person holds |
| May a file give any role? | Only roles that apply within a scope. A platform role, the Administrator role least of all, is refused |
| What if one row of a person is wrong? | None of that person's rows is applied; other people are unaffected |
| How big may a file be? | 1000 rows, 512 KB; ten real imports an hour; one at a time; two minutes |
| What if the first Super Admin never accepts the invitation? | A restart sends the link again while there is still no administrator |

## Open

### OQ-A15. Who provides departments, divisions and batches, with their codes?

- **Why it matters:** until something implements `ScopeDirectory`, no role that needs a place (HOD, CR, BR, Student) can be given by the screen or by an import, and the Phase 3 gate (a seeded user of each role signs in) cannot be met. Super HOD and Faculty work today.
- **Depends on it:** the Phase 3 gate, and the Phase 4 per-role end-to-end tests.
- **What it must provide:** `exists(kind, id)`, `idOf(kind, code)` and `codesOf(kind, ids)` for the kinds `DEPARTMENT`, `DIVISION` and `BATCH`, with one rule: within a tenant and a kind a code names at most one live place, compared the same way every time. A division called `A` in several departments does not satisfy it; `CSE-A` does.
- **Options:** the owner of the timetable and academic reference data implements the port when their module exists (the contract is in the [integration contract](integration-contract.md)); or security owns minimal reference tables (rejected in [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md)); or a directory read from configuration for local use (rejected: it invents data).
- **Recommendation:** ask the owner of the academic data to implement the port, and agree the code format with them first, because the codes are what people will write in files.

## Open: not needed for Phases 0 to 3

### OQ-A6. What happens when an HOD is absent?

No delegate or acting HOD is documented, and one HOD per department is recorded. **Recommendation:** none in version 1. A Super HOD acts. Revisit if it becomes a real problem.

### OQ-A7. Must a CR or BR approve, or only acknowledge?

The data model has an acknowledged status that does not block. The rules do not say whether a requested CR or BR approval is required before a change applies. This belongs to the ticket module. **Recommendation:** treat it as the ticket module's decision. Authorization only tells whether they may.

### OQ-A11. Who sees tickets, and who reads the audit log?

Not defined: what a faculty member who is not on a ticket may see, what Super HOD may see beyond its scope, the right to read the system-wide audit log, and its retention. Today the policy denies all of these except the flat Super Admin read-all of ticket audit logs. **Recommendation:** involved people and department scope see tickets; keep Kibootia's 365-day retention until the owner chooses.

### OQ-A12. HOD-authored permanent changes and self-approval by HOD or Super HOD

Who raises a permanent change, whether an HOD's own permanent change needs a second approver, and whether HOD or Super HOD may self-approve are not defined. Today they are **denied**: no HOD or Super HOD holds the self-approve permission. **Recommendation:** the HOD raises and approves their department's permanent changes; a Super HOD approves an HOD's own. Confirm.

## Document repairs

Documents that disagree with each other or with a decision. Planned as W4.5, not edited now.

| Document | Problem | Fix |
| --- | --- | --- |
| [data model fixes](../../research/data-model-fixes.md), F-A2 | Proposes a local password and an auth provider, which [ADR 0001](../../decisions/0001-authentication-follows-kibootia.md) does not adopt | Add a pointer to the decision |
| [data model fixes](../../research/data-model-fixes.md), F-G2 | Removes Faculty and Student from roles. Timetia keeps them as role templates ([ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md)) | Note the outcome |
| [Super Admin](../../shared/roles/super-admin.md) and [roles and powers](../../modules/lecture-rescheduling/rules/permissions.md) | Do not mention user and role administration, which [ADR 0003](../../decisions/0003-role-administration-by-import-and-super-admin.md) assigns. Dated 2026-10-27 yet contain later changes | Update after the work exists |
| [rules index](../../modules/lecture-rescheduling/rules/README.md) and the module changelog | Describe shared courses as skipped. The shared-courses rule and a later changelog entry enable them | Align |
| [ticket lifecycle](../../modules/lecture-rescheduling/rules/ticket-lifecycle.md) | Says student ticket visibility is open. The module's open questions say results only | Align |
| [archived spec](../../modules/lecture-rescheduling/_archive/original-spec.md) versus [conflict rules](../../modules/lecture-rescheduling/rules/conflict-rules.md) | The spec lets Super Admin override a ticket. The rules say nobody overrides a conflict | The rules win, and the archive says so |
| [STRUCTURE.md](../../STRUCTURE.md) | Describes folders and files that do not exist (`product/`, `engineering/`, a permissions matrix, a README) | Update or create them as the migration proceeds |
