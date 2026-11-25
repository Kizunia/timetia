# Authentication and authorization audit

- **Type:** research
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

The evidence behind the [identity and access documents](../architecture/identity/README.md). Non-authoritative and time-bound: it records what was found on 2026-11-11. Where a finding became a rule, a design or a task, the section says where it went.

**Nothing was changed, built or tested during the audit.** The work was reading files and running read-only `git` and search commands. Statements about tests mean the tests exist and were read. They do not mean the tests were run.

## 1. How the repositories relate

| Fact | Evidence |
| --- | --- |
| Timetia's branch `ishan/authn-authz` is Kibootia's `main` (commit `4cea34f`) plus `timetia-docs` | A comparison of the branch with `4cea34f`, excluding `timetia-docs`, shows no difference in `backend`, `frontend`, `infra`, `compose.yaml`, `.github` or `scripts` |
| The branch is 101 commits ahead of Timetia's `main`. 30 are documentation commits and 71 are a merge of Kibootia's history | `git rev-list --left-right --count` against `main` and `docs`; the merge base of `docs` and the branch is the tip of `docs` |
| Timetia's `docs` branch holds the documentation commits only. The Kibootia code and `kibootia-docs` come with the merge | Branch comparison |
| Timetia has no code of its own. Every `kibootia` name is still in place | A search of the code and configuration outside `timetia-docs` finds no Timetia-specific file |
| The git status given at the start of the session named `docs` as the current branch. The checked-out branch is `ishan/authn-authz`. No branch was changed | `git branch --show-current` |

Kibootia's own working tree (a separate checkout) is on a branch named `docs/authorization-requirements`, at the same commit as its `main`, with **34 uncommitted paths** (13 modified, the rest new) and no matching branch on the remote (the remote has `m-0`, `m-2` and `main` among its heads). That work is described in section 4 in words and is not linked, because it is not committed.

Where this went: [ADR 0002](../decisions/0002-scoped-roles-on-the-current-kibootia-contract.md), [current state](../architecture/identity/current-state.md).

## 2. What Timetia inherited, by area

Details and class names are in [current state](../architecture/identity/current-state.md). Summary of the comparison:

| Area | Inherited from Kibootia | Timetia needs | Gap |
| --- | --- | --- | --- |
| Sign-in | Keycloak, Next.js server holding the session, PKCE | Nothing documented | None to build. Decision: [ADR 0001](../decisions/0001-authentication-follows-kibootia.md) |
| Tokens | Signature, issuer, expiry, audience, service accounts refused | Same | Missing edge-case tests |
| Identity record | `user_profile` keyed by the token `sub`, created at first request | A link to faculty and student records | Person link missing |
| Roles | Stored in the database. `Administrator` and `User` built in. Permissions declared in code | Seven roles with scope | Roles and scope missing |
| Scope | All, own, none | Department, division, batch, and ticket relations | Scope model missing |
| Admin | Screens and API for users, roles, audit | Assignment with scope; import | Scope in screens and an import job missing |
| Audit | Append-only, ids only | Ticket audit with before and after; security events | Security events missing; ticket log belongs to the ticket module |
| Names | `kibootia` | Timetia names | Rename |
| Production | Local Keycloak setup only | A deployment eventually | Out of Timetia's first need |

## 3. Findings

The classified list is in [current state](../architecture/identity/current-state.md). Defects and gaps are in the [hardening backlog](../architecture/identity/hardening-backlog.md), R1 to R7 and T1 to T11. The one finding checked directly by reading the source is R1: `RoleAssignmentService` restricts only the Administrator role, while `RoleService.requireGrantable` shows the rule that is missing.

Documentation contradictions are in the [open questions](../architecture/identity/open-questions.md), under "Document repairs".

## 4. Kibootia's uncommitted authorization work

Summarized from reading the files in Kibootia's working tree. It is a **proposal by the same author, not accepted, and not part of Timetia's baseline** ([ADR 0002](../decisions/0002-scoped-roles-on-the-current-kibootia-contract.md)).

- Four draft decisions: adopt a set of authorization requirements as committed scope, a domain model, a decision contract (version 2), and an evaluation engine.
- About 50 numbered requirements, most marked approved, with a traceability table against today's code and a roadmap of eleven further Kibootia milestones.
- The model: grants limited by scope, relationship or condition, with assignments at scope nodes, groups, delegation, governance and multi-tenancy.
- It would supersede Kibootia's decision to start with role-based authorization and no policy engine.
- The reference scenarios for Timetia in that work were written from a small share of Timetia's documents, and no Timetia application code existed.

What Timetia takes from it: the idea of scoped assignments, a scope filter for lists, and the boundary rule that authorization decides whether a person may and the application decides whether the operation is valid. What it does not take: groups, delegation, an expression or policy engine, multi-tenancy, caching.

Where this went: [target architecture](../architecture/identity/target-architecture.md), [ADR 0002](../decisions/0002-scoped-roles-on-the-current-kibootia-contract.md).

## 5. Timetia's own requirements, in short

From `timetia-docs`:

- No document says how users sign in. The module is standalone in version 1 with seeded users and roles. ERP single sign-on is a later target.
- Seven roles. CR is for theory lectures of a division and BR is for lab and tutorial lectures of a batch. HOD is department scope, Super HOD is across departments, Faculty acts on the lectures they teach, Student is read only.
- A faculty member can always apply their own change. The HOD must approve permanent changes. Author, affected faculty and HOD can each revert before the lecture starts. Nobody overrides a conflict.
- Ticket audit is append-only and visible to the author, reviewers, HOD, CR and BR in scope, and Super Admin.

The rule tables are in the [role and scope model](../architecture/identity/role-and-scope-model.md). What the documents do not define is in the [open questions](../architecture/identity/open-questions.md).

## 6. Commands and checks run

Read-only. No test, build, installation or application start.

- `git` queries: branch names and positions, merge bases, left and right commit counts, a diff statistic between the branch and `4cea34f` for the code folders, the remote's branch heads for the Kibootia checkout, status and counts of uncommitted paths there.
- File listings and searches in both repositories.
- Reading of: the Timetia documents, Kibootia's committed documents and decisions, the uncommitted Kibootia files, and the backend, frontend and infrastructure files named in the documents above.
- One check that Docker is not running at the time. The user later said it was running. No test was started.

An edit made during the session to the role assignment code and two test files, adding a fix for R1, was reverted at the user's request. Nothing in the repository's code is changed.

## 7. Not checked

Anything that needs a running system or a CI result: whether the inherited tests pass in Timetia, whether the Keycloak configuration applies cleanly under new names, how the identity provider behaves under load. Kibootia's own documents record CI runs for its tests. Those runs are Kibootia's and were not repeated.
