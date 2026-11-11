# Role and scope model

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

The roles, their scopes and a **draft** permission catalogue for Timetia. Everything is derived from the documents on 2026-11-11: [roles and powers](../../modules/lecture-rescheduling/rules/permissions.md), the [approval model](../../modules/lecture-rescheduling/rules/approval-model.md), [reverts](../../modules/lecture-rescheduling/rules/reverts.md), [ticket lifecycle](../../modules/lecture-rescheduling/rules/ticket-lifecycle.md), [issues](../../modules/lecture-rescheduling/rules/issues-from-cr-and-br.md) and the [role files](../../shared/roles/README.md). Where those documents are silent, this file marks the cell `?` and the question is in [open-questions.md](open-questions.md). This file does not invent a rule.

It is a proposal until the owner has answered the questions marked blocking.

## Roles and scopes

| Role | Scope type | Meaning of the scope | Note |
| --- | --- | --- | --- |
| Super Admin | global | Everything | Configuration only. Not part of routine approval. Cannot override a conflict. |
| Super HOD | global | All departments | Everything an HOD can, across departments. |
| HOD | department | One department, with its divisions and batches | One active HOD per department (data model fix F-G2). |
| Faculty | none, plus a relation | The lectures the person teaches | Whether Faculty is a role or only a profile is [OQ-A4](open-questions.md). |
| CR | division | One division | **Theory** lectures only. A class can have several CRs. |
| BR | batch | One batch | **Lab and tutorial** lectures only. |
| Student | none, plus membership | Their own division, batch and shared-course batches | Read only. |

Scope hierarchy: department contains divisions, a division contains batches. A scoped permission held at a department applies to everything under it.

CR and BR rights also depend on the **session type** of the lecture (theory, lab or tutorial). That is a fact of the record, not a scope.

## Facts the authorization policy needs about a ticket

The ticket module supplies these through the ticket access facts port. Security code reads them and returns allow or deny. It does not change them.

- The author, and whether the author acted for an absent faculty.
- The affected faculty.
- The requested reviewers.
- The faculty mentioned on an issue, and who opened it.
- The department, division and batch of the lecture, **several batches** when it is a shared course.
- The session type.

The ticket module keeps rules that depend on state: whether the lecture has started, whether the ticket is final, whether approvals count for the current revision, conflict checks. See [target architecture](target-architecture.md), principle 3.

## Draft permission catalogue

Names follow `<module>.<resource>.<action>`. The module names `rescheduling` and `timetable` are working names for the lecture-rescheduling and live timetable parts, and the module owners confirm them. Existing platform permissions (`access.roles.*`, `identity.users.*`, `audit.*`) are unchanged.

| Permission | Meaning | Reach |
| --- | --- | --- |
| `timetable.live.view` | See the live timetable | Scope or membership |
| `timetable.config.manage` | Rooms, slot grid, calendar, holidays, exam weeks, notification settings | Global |
| `rescheduling.tickets.create` | Raise a reschedule, exchange, extra, cancel or substitute ticket | Relation: lecture taught, or department when acting for an absent faculty |
| `rescheduling.tickets.cancel` | Cancel a lecture directly, with a reason | Relation or department |
| `rescheduling.tickets.self-approve` | Apply one's own change without a review | Relation |
| `rescheduling.tickets.review` | Be a requested reviewer and approve | Relation, or scope with session type |
| `rescheduling.tickets.reject` | Reject with a reason | Same as review |
| `rescheduling.tickets.approve-permanent` | Approve a permanent change | Department |
| `rescheduling.tickets.revert` | Revert before the lecture starts | Relation, or department |
| `rescheduling.tickets.edit-own` | Edit and resubmit, escalate a rejection, withdraw | Author |
| `rescheduling.tickets.view` | See a ticket | Relation or scope |
| `rescheduling.tickets.audit.view` | Read a ticket's audit log | Relation or scope |
| `rescheduling.issues.open` | Open an issue | Scope with session type |
| `rescheduling.issues.close` | Close an issue | Opener or mentioned faculty |

Every in-app notification belongs to a user and is read by that user. That permission belongs to the notifications owner.

## Role by permission, as documented

`Y` allowed. `S` allowed within the role's scope. `R` allowed through a relation to the ticket or lecture. `—` not allowed or not mentioned. `?` not defined by the documents.

| Permission | Super Admin | Super HOD | HOD | Faculty | CR | BR | Student |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `timetable.live.view` | Y | Y | S | R (own, across divisions) | S | S | S |
| `timetable.config.manage` | Y | — | — | — | — | — | — |
| `rescheduling.tickets.create` | — | R (acting for absent faculty) | R (acting for absent faculty) | R | — | — | — |
| `rescheduling.tickets.cancel` | — | R | R | R | — | — | — |
| `rescheduling.tickets.self-approve` | — | ? | ? | R (always) | — | — | — |
| `rescheduling.tickets.review` | — | R | R | R (if requested) | S (theory, if requested) | S (lab, tutorial, if requested) | — |
| `rescheduling.tickets.reject` | — | R | R | R (as reviewer) | S (theory) | S (lab, tutorial) | — |
| `rescheduling.tickets.approve-permanent` | — | Y | S | — | — | — | — |
| `rescheduling.tickets.revert` | — | R | S (any change in the department) | R (author, or affected faculty) | — | — | — |
| `rescheduling.tickets.edit-own` | — | R | R | R (author) | — | — | — |
| `rescheduling.tickets.view` | ? | R | S | R (own, requested, mentioned) | R (as reviewer or receiver) | R (as reviewer or receiver) | — (results only) |
| `rescheduling.tickets.audit.view` | Y | ? | S | R (author, reviewer) | S | S | — |
| `rescheduling.issues.open` | — | — | — | — | S (theory) | S (lab, tutorial) | — |
| `rescheduling.issues.close` | — | — | — | R (if mentioned) | R (if opener) | R (if opener) | — |

Not decided by the documents, and therefore not filled in: who may raise a permanent change, whether an HOD or Super HOD may self-approve, how a cross-department case is decided, who can revert a plain reschedule, extra lecture or permanent change when they are its author, and what a non-involved faculty member, Super HOD or Super Admin may see of tickets. These are [OQ-A3, OQ-A5, OQ-A11 and OQ-A12](open-questions.md).

**Nobody can override a conflict**, including the HOD and Super Admin ([conflict rules](../../modules/lecture-rescheduling/rules/conflict-rules.md)). This is a rule of the ticket module, and no permission grants it.

## From this table to roles in the system

The catalogue becomes `PermissionProvider` declarations in the modules that own each permission. The role templates become seeded roles with a scope type. The role by permission table becomes the seed data and the test matrix in [testing and verification](testing-and-verification.md). Work packages W2.2 and W2.3 in the [implementation plan](implementation-plan.md).
