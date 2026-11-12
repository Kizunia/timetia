# Role and scope model

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

The roles, scopes and permission catalogue that Phase 2 implements, with the rule for each permission. **Implemented** in the `academic` module: `AcademicPermissions` (the catalogue), `AcademicRoles` (the role templates, seeded at startup) and `AcademicAccessPolicy` (the rules). The unit test `AcademicAccessPolicyTest` has one test per row below, allowed and denied. It follows [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md). Everything is derived from [roles and powers](../../modules/lecture-rescheduling/rules/permissions.md), the [approval model](../../modules/lecture-rescheduling/rules/approval-model.md), [reverts](../../modules/lecture-rescheduling/rules/reverts.md), [ticket lifecycle](../../modules/lecture-rescheduling/rules/ticket-lifecycle.md), [issues](../../modules/lecture-rescheduling/rules/issues-from-cr-and-br.md) and the [role files](../../shared/roles/README.md).

Where those documents are silent, the right is **denied** and listed under "Not granted, because the documents do not say". This file does not invent a rule. The rules that the owner has not confirmed are marked **default** and come from ADR 0004, point 8.

Implementation status is in [implementation-plan.md](implementation-plan.md).

## Roles and scopes

| Role | Scope type of its assignment | Meaning | Note |
| --- | --- | --- | --- |
| Super Admin | none (the built-in Administrator role) | Everything on the platform | Holds platform administration and the flat configuration and read-all permissions. **Holds no ticket permission.** Cannot override a conflict. |
| Super HOD | `GLOBAL` | All departments | Everything an HOD can, across departments. |
| HOD | `DEPARTMENT` | One department, with its divisions and batches | One active HOD per department (data model fix F-G2). |
| Faculty | `GLOBAL` | Anywhere, but rights come from a relation to the lecture or ticket | A role template that carries permissions ([OQ-A4](open-questions.md) default). |
| CR | `DIVISION` | One division | **Theory** lectures only. A class can have several CRs. |
| BR | `BATCH` | One batch | **Lab and tutorial** lectures only. |
| Student | `BATCH` | One batch | Read only. A theory lecture of a division lists every batch of that division, so a batch assignment sees it. |

A person may hold several assignments, for example an HOD of two departments or a CR of two divisions. There are no expiry dates yet ([OQ-A2](open-questions.md) default).

## Two kinds of permission

All Timetia ticket and timetable permissions are **policy-decided** ([ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md)). A flat check never satisfies them. They are decided per record by the policy, from the caller's grants and the record's facts. Each is one of:

- **Scope-based (S).** Allowed when one of the caller's grants **covers** the record.
- **Relation-based (R).** Allowed when the caller is one of the named people on the record. The caller must hold the permission through a role.
- **Both (R and S).** The relation must hold, and a grant must cover the record.

A permission is never both "or" ways: where a role needs a relation (Faculty) and another needs scope (HOD), they are two permissions. This keeps a Faculty member's `GLOBAL` assignment from covering every record.

**A grant covers a record when:** the grant is `GLOBAL`; or it is `DEPARTMENT` and its id equals the record's department; or `DIVISION` and its id equals the record's division; or `BATCH` and its id is among the record's batches. A missing id in the facts never matches.

**The session rule.** For the actions marked "session rule", a `DIVISION` grant applies only to a **theory** lecture, and a `BATCH` grant applies only to a **lab or tutorial** lecture. `DEPARTMENT` and `GLOBAL` grants have no session limit. This is how CR is limited to theory and BR to lab and tutorial.

The people lists in the facts are **user ids**, resolved by the module that owns the records. A person directory is not part of this phase.

## Facts the policy reads

The module that owns a record passes an object with these facts to the check. All of them extend the tenant of the record, so a record of another tenant is always refused.

| Facts | Contents |
| --- | --- |
| Lecture | department id, division id, **all** batch ids, session type (theory, lab, tutorial), user ids of the teaching faculty |
| Ticket (lecture plus) | author user id, user ids of the affected faculty, user ids of the requested reviewers, whether the change is permanent |
| Issue (lecture plus) | opener user id, user ids of the faculty mentioned |

The module keeps every rule that depends on state: whether the lecture has started, whether a ticket is final, whether approvals count for the current revision, whether a faculty member is absent, conflicts. See [target architecture](target-architecture.md), principle 3.

## Permission catalogue

Names are `<module>.<resource>.<action>`. The module names `rescheduling` and `timetable` are working names, and the module owners confirm them. The names are stored in role definitions, so moving a declaration to another module later does not change them.

| Permission | Kind | Rule | Held by |
| --- | --- | --- | --- |
| `timetable.live.view` | S | A grant covers the lecture | Super HOD, HOD, CR, BR, Student |
| `timetable.live.view-own` | R | The caller teaches the lecture | Faculty |
| `timetable.live.view-all` | flat | Everything | Super Admin |
| `timetable.config.manage` | flat | Rooms, slot grid, calendar, holidays, exam weeks, notification settings | Super Admin |
| `rescheduling.tickets.create` | R | The caller teaches the lecture | Faculty |
| `rescheduling.tickets.create-on-behalf` | S | A grant covers the lecture | Super HOD, HOD |
| `rescheduling.tickets.cancel` | R | The caller teaches the lecture | Faculty |
| `rescheduling.tickets.cancel-on-behalf` | S | A grant covers the lecture | Super HOD, HOD |
| `rescheduling.tickets.self-approve` | R | The caller is the author and teaches the lecture | Faculty |
| `rescheduling.tickets.review` | R and S | The caller is a requested reviewer; a grant covers the lecture; session rule | Faculty, Super HOD, HOD, CR, BR |
| `rescheduling.tickets.reject` | R and S | Same as review | Faculty, Super HOD, HOD, CR, BR |
| `rescheduling.tickets.approve-permanent` | S | A grant covers the lecture | Super HOD, HOD |
| `rescheduling.tickets.revert` | R | The caller is the author or an affected faculty, and the change is **not** permanent (default) | Faculty |
| `rescheduling.tickets.revert-in-scope` | S | A grant covers the lecture | Super HOD, HOD |
| `rescheduling.tickets.edit-own` | R | The caller is the author | Faculty, Super HOD, HOD |
| `rescheduling.tickets.view` | R | The caller is the author, an affected faculty or a requested reviewer | Faculty |
| `rescheduling.tickets.view-in-scope` | S | A grant covers the lecture; session rule | Super HOD, HOD, CR, BR |
| `rescheduling.ticket-audit.view` | R | The same people as `view` | Faculty |
| `rescheduling.ticket-audit.view-in-scope` | S | A grant covers the lecture; session rule | Super HOD, HOD, CR, BR |
| `rescheduling.audit.view-all` | flat | Any ticket's audit log | Super Admin |
| `rescheduling.issues.open` | S | A grant covers the lecture; session rule | CR, BR |
| `rescheduling.issues.close` | R | The caller opened the issue or is a mentioned faculty | Faculty, CR, BR |

"Flat" permissions are ordinary permissions. The built-in Administrator holds them implicitly, like every other flat permission. Every other permission above is policy-decided, so the Administrator does not hold it.

Existing platform permissions (`access.roles.*`, `identity.users.*`, `audit.*`) are unchanged.

## Rules the documents state that no permission grants

- **Nobody can override a conflict**, including the HOD and Super Admin ([conflict rules](../../modules/lecture-rescheduling/rules/conflict-rules.md)). No permission for it exists.
- A Student cannot raise a ticket, open an issue or see a ticket or its log. A Student sees the live timetable only. Students see the result of a change, not the ticket.
- CR and BR have no assign powers.

## Not granted, because the documents do not say

These are denied. Each is a question in [open-questions.md](open-questions.md), and granting one later is a role change and a rule, not a redesign.

- Who may raise a permanent change beyond the HOD, and whether an HOD's own permanent change needs a second approver.
- Whether an HOD, Super HOD or Super Admin may self-approve.
- What a faculty member who is not on a ticket, a Super HOD (beyond scope) or a Super Admin may see of tickets, other than the flat audit view.
- How a cross-department case is decided beyond "the department of the lecture's division".
- Who can revert a permanent change other than an HOD or Super HOD.

## From this model to the system

- The catalogue is declared as `PermissionProvider` entries by the `academic` module in Phase 2. When the ticket and timetable modules exist, their owners can take over the declarations. The names stay.
- The role templates are seeded roles with the scope types above.
- The rules are the policy in the `academic` module, with one test for every row, allowed and denied. See [testing and verification](testing-and-verification.md).
