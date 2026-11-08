# 0044 Authorization enforcement conventions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Context

[ADR 0027](0027-central-authorization-decision-point.md) fixes one decision point, `can(principal, action, resource)`. How it is used was open: where checks are written, how lists are restricted, how fresh permissions are after a role change (OQ-35), and whether machine clients without a human user are accepted. Most authorization defects are a check in the wrong place or a missing one, and an architecture test can only enforce a written rule.

## Decision

**Where checks live.** On **application use-case methods**, not on controllers, because use cases are also called by other modules, jobs and event handlers. Two forms, both going through the decision point:

- an annotation for permission-only checks, for example `@RequiresPermission(NotesPermissions.CREATE)`;
- a call such as `authorization.require(UPDATE, note)` after loading the record, for rules that need the record (ownership, tenant).

An ArchUnit rule fails the build when a public use-case method has neither a check nor an explicit `@NoAuthorization("reason")` marker.

**Permission names.** `<module>.<resource>.<action>`, for example `identity.users.invite` or `notes.notes.update-any`. Each module declares its permissions as constants; they are registered at startup so the role editor can list them.

**Lists.** The decision point also answers `scopeFor(action)`, returning `ALL`, `OWN` or `NONE` (richer scopes later). Each module turns the scope into a query condition, so filtering happens in the database and paging stays correct.

**Freshness.** No permission cache at first. Each API request runs one indexed query that loads the caller's profile status and permission set; this also enforces immediate denial of disabled users ([ADR 0041](0041-user-lifecycle-and-account-model.md)). Role changes apply on the next request, on any number of instances. The documented upgrade, if measurement shows a need, is an in-memory cache validated against a per-tenant authorization version number.

**Machine clients.** Only tokens that belong to a user are accepted. Tokens issued to a service account (client credentials) are rejected by default. Service principals with their own roles are a future recipe.

**Email verification** is a fact the decision point evaluates ([ADR 0041](0041-user-lifecycle-and-account-model.md)).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Checks on controllers | Calls from other modules, jobs and event handlers would be unprotected |
| Each module filters lists its own way | Inconsistent and easy to get wrong |
| Load all rows and filter in memory | Breaks paging and does not scale |
| Cache invalidated locally on change | Wrong as soon as there are two instances |
| Short time-based cache | Role removals take effect late |
| Support machine clients now | More modelling with no current use in the reference application |

## Consequences

- One extra query per API request, cheap with the right index.
- Every protected use case needs allowed and denied tests, and list endpoints test their scope ([verification standard](../engineering/testing/verification-standard.md)).
- The decision point's port grows a second operation (`scopeFor`); an external engine replacing the Java policies must answer it too.

## Status

accepted. Resolves OQ-35. Extends [ADR 0027](0027-central-authorization-decision-point.md), which stays accepted.

## Related

[Authorization](../architecture/identity/authorization.md), [architecture enforcement](../architecture/architecture-enforcement.md).
