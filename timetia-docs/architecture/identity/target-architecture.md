# Target architecture

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

The intended design for Timetia's authentication and authorization. It is a proposal for the owner's review. **None of it is built.** It extends what Timetia inherited from Kibootia ([current state](current-state.md)) and follows [ADR 0001](../../decisions/0001-authentication-follows-kibootia.md), [ADR 0002](../../decisions/0002-scoped-roles-on-the-current-kibootia-contract.md) and [ADR 0003](../../decisions/0003-role-administration-by-import-and-super-admin.md).

## Principles

1. **Keep what works.** Authentication, token validation, stored roles, the decision point, the checks on every use case, the audit log and the 404 versus 403 rule stay as Kibootia built them.
2. **Add only what Timetia's rules need.** The need is rights that depend on a department, division or batch, and on a person's relation to a ticket. Nothing else is added.
3. **Authorization says "may they". The module says "is it valid now".** Whether a person may revert a ticket is authorization. Whether the lecture has already started is the ticket module's rule. This line keeps ticket workflow out of the security code and security out of the ticket code.
4. **Timetia knowledge arrives through ports.** Security code does not know what a department is. It asks a port.
5. **Revocation is immediate.** A role change applies on the next request. No permission cache is added.

## Parts

### 1. Identity (unchanged, renamed)

Keycloak holds credentials. The backend validates the token, then maps the `sub` to a `user_profile`. Timetia changes identity-facing names (realm, clients, audience, cookies, the CSRF header, the page brand) and turns self-registration off ([ADR 0005](../../decisions/0005-identity-hardening-decisions.md)). Plan: Phase 1, W1.6 and W1.8.

### 2. Scoped role assignments

A role assignment gains a **scope**: global, department, division or batch, and the id of that department, division or batch.

- A role is created from a Timetia template (Super Admin, Super HOD, HOD, Faculty, CR, BR, Student) that says which scope type it needs. Assigning it without a matching scope is refused.
- A permission held through a scoped assignment applies only inside that scope. A department scope includes its divisions and batches. A division scope includes its batches.
- Optional validity dates exist only if CR and BR roles are to expire at term end ([OQ-A2](open-questions.md)).
- Platform roles (`Administrator`, `User`, custom roles) stay as they are, with no scope.

**Storage is decided** ([ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md)): the access tables are extended. A role gets an optional scope type (`GLOBAL`, `DEPARTMENT`, `DIVISION`, `BATCH`; none for platform roles). A role assignment gets a scope id (all zeros means no scope), and its key becomes user, role and scope. The migration is additive.

Two invariants are enforced when a role is created or edited: a role that holds any policy-decided permission must have a scope type, and a role that has a scope type holds only policy-decided permissions. The older role endpoint assigns only unscoped roles. A role that needs a scope is assigned through the scoped-role endpoint, which checks the scope.

### 3. Scope-aware policy and list filter

The contract Timetia extends has `can`, `require`, `scopeFor` and a per-record rule hook (`ResourcePolicy`). Timetia adds, without changing those signatures:

- **Policy-decided permissions.** A permission can be declared as decided per record. The flat checks (`@RequiresPermission`, the permission-only `can` and `scopeFor`) never satisfy it, and the built-in Administrator does not hold it implicitly. It is used only by checking a record, where a module's policy decides from the caller's grants and the record's facts. Forgetting the record check denies instead of over-allowing. An `@RequiresPermission` that names such a permission stops the application at startup.
- **Grants.** A generic platform port returns the caller's grants for an action as a kind and an optional id. The platform does not know the words department, division or batch. The Timetia module declares those kinds.
- **A Timetia policy** (module `academic`) that implements the rules in the [role and scope model](role-and-scope-model.md) from the grants and the facts.
- **A reach for lists.** For an action, the caller's reach as "everything" or a set of ids per kind, which a query can apply in the database. This is what `scopeFor` (all, own, none) cannot express. The relation part of a list (my own tickets) comes from the module's own columns.

### 4. Ports Timetia code implements

| Port | Purpose | Likely owner |
| --- | --- | --- |
| Scope directory | Does this department, division or batch exist. Without an implementation, assigning a scoped role is refused | The owner of department, division and batch reference data |
| Ticket and lecture access facts | The facts that rights depend on, with people as user ids (see [role and scope model](role-and-scope-model.md)) | The lecture-rescheduling and timetable modules |

Security code defines each port. The owners implement them. Until an implementation exists, tests use fakes. A person directory is deferred, because the facts carry user ids.

### 5. Provisioning and administration

Users and their scoped assignments are imported from files by an idempotent job, and Super Admin adjusts assignments in the existing administration screens, extended with scope ([ADR 0003](../../decisions/0003-role-administration-by-import-and-super-admin.md)). Accounts are created through the existing identity provider port and invitation lifecycle. Every import and change is audited.

### 6. Audit and security events

Kibootia's audit rules stay: written in the same transaction, user ids only, no names or emails. Additions: assignment and import events with scope, and a record of refused requests, failed sign-ins at the API and disabled-account refusals ([hardening backlog](hardening-backlog.md), R7). Ticket audit with before and after values is the ticket module's own log.

## Where Timetia code lives

In its own module or modules under the existing package root, declared with `@ApplicationModule` like Kibootia's. They depend only on the exported interfaces of `platform`. Kibootia's boundary and authorization checks (Modulith verification, the ArchUnit rules) apply to them without exceptions.

## Deliberately out of scope

Groups, delegation of role assignment, an attribute-based policy engine or external policy service, live multi-tenancy, permission caching, and machine-to-machine clients. Each can be added later if a Timetia requirement appears. Kibootia's own draft work on several of these is uncommitted and not a dependency.

## What this design does not decide

The business rules of tickets, the ticket and timetable data model, notifications, and how the ERP is integrated later. Those belong to the module owners.
