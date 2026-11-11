# 0049 Milestone 2 implementation decisions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-09

> **Implementation:** observed, with the `m-2` branch tests passing locally on 2026-11-09 (71 fast tests, 137 integration tests). Becomes verified when the CI run of pull request #4 is recorded in [implementation status](../product/roadmap/implementation-status.md).

## Context

[Milestone 2](../product/roadmap/milestone-2-platform-conventions.md) built the conventions that [ADR 0012](0012-flyway-managed-schema-evolution.md), [0028](0028-tenant-ready-schema.md), [0031](0031-transactional-outbox-and-locked-jobs.md), [0033](0033-uuidv7-identifiers.md), [0034](0034-soft-delete-with-retention-purge.md), [0039](0039-spring-modulith-module-boundaries-and-outbox.md), [0043](0043-http-api-conventions.md), [0044](0044-authorization-enforcement-conventions.md) and [0046](0046-audit-log-design.md) describe. Those decisions fix what the platform does but leave open how the pieces fit together without module cycles, and several choices only became visible while building. Each one below was forced by a test or a rule that failed otherwise.

## Decision

**Authorization contract in `platform`, decision point in `access`.** `Authorization`, `@RequiresPermission`, `@NoAuthorization`, `Scope`, `Permission` and the `PolicyDecisionPoint` port live in `platform.authorization`; the Java policies live in `access`. Milestone 3 has `access` write audit entries, so if `audit` had to depend on `access` for the annotations, the two modules would form a cycle that Spring Modulith rejects. Without a decision point bean, `Authorization` denies everything.

**Ownership scope is data, not code.** A permission with a registered `-any` sibling (`notes.notes.update`, `notes.notes.update-any`) means "my own records"; the sibling widens it to every record in the tenant. A permission without a sibling (`notes.notes.restore`) is administrative and means all. `scopeFor` returns `NONE`, `OWN` or `ALL` from that rule. An action that is not a declared permission is an error, and a `@RequiresPermission` naming an undeclared permission stops startup.

**Permissions in milestone 2 come from the built-in `user` role in code**, behind a `PermissionSource` port that milestone 3 replaces with the stored roles. No real user holds an administrative permission until then; the administrative endpoints are proven by tests that grant permissions.

**A record the caller may not view is 404, one they may view but not change is 403.** The use case loads the record, answers 404 unless the caller may view it, and only then checks the action.

**Tenancy fails closed.** The tenant is bound after bearer authentication and read by Hibernate when a session opens, so it must be bound before a transaction starts. With no tenant bound, Hibernate sees the nil tenant and finds nothing, and persisting an entity is refused.

**`@DurableEventListener` replaces `@ApplicationModuleListener`.** The latter starts its transaction before anything can bind the tenant, so Hibernate would open the session with no tenant. `@DurableEventListener` runs an interceptor that binds the tenant from the event's `EventContext`, continues the publisher's trace, acts as the system, opens its own transaction and records `(event ID, handler)` in `processed_event`, skipping the call if the row already exists. A failing handler rolls the mark back, so the registry's retry runs it again. Architecture rules 10 and 11 enforce the annotation and its signature.

**Retry is a policy over the Spring Modulith registry.** The registry counts delivery attempts. The retry job resubmits a failed event after a doubling wait (30 s, up to 1 h) until `maxAttempts` (5), then leaves it failed. A delivery that never finished is treated as lost after 5 minutes. Given-up events stay visible, and an administrator can retry them regardless of the count.

**The failed-event view reads the tenant from the event's JSON.** The registry table has no tenant column, so the platform filters on `$.context.tenantId` in the serialised payload and lists only events that carry a context. The API returns metadata, never the payload.

**Jobs.** ShedLock 7.11.0 with the JDBC provider takes the lock; every `@Scheduled` method must carry `@SchedulerLock` (rule 9). A job and its schedule are separate beans, and only the schedule is switched off by `kibootia.jobs.enabled=false`, so tests call a job directly without a timer racing them. One retention job runs every `RetentionPurgeable` as the system, once per tenant, each in its own transaction; a failure in one does not stop the others.

**Persistence.** Hibernate 7.4's `@UuidGenerator(VERSION_7)`, `@TenantId`, `@Version` and `@SoftDelete(TIMESTAMP)` work together on a mapped superclass with MySQL 8.4, so OQ-41 needed no fallback. Timestamps are `DATETIME(6)` holding UTC. Entity queries hide deleted rows, so restoring and purging use native SQL with an explicit `tenant_id` condition. Unique keys include the generated active-marker column.

**Errors and contract.** `ApiException`, `ApiFieldError` and `ProblemCodes` live in `platform.errors`, apart from `platform.web` which turns them into responses; use cases can throw them without depending on the web layer, which rule 1 would otherwise forbid. A test writes `backend/openapi.json` (no server URL, bearer scheme, a `Problem` schema on every operation, stable operation IDs, required and nullable fields marked), and CI fails if running the build changes it. The frontend client is generated from that file and CI fails if it is stale. The OpenAPI description and Swagger UI are management endpoints, exposed only when `management.endpoints.web.exposure.include` names them, which `pnpm dev` does and production does not.

**Test infrastructure.** Each distinct Spring test context starts its own MySQL container, so the number of cached contexts is capped at four and MySQL runs without the performance schema.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Authorization annotations in `access` | Creates a module cycle in milestone 3 |
| Ownership as a per-module policy for every resource | Every module repeats the same rule; the `-any` convention covers the common case and `ResourcePolicy` covers the rest |
| `@ApplicationModuleListener` plus a manual tenant bind in the handler | The session is already open with no tenant; the bind comes too late |
| A `tenant_id` column on the registry table | Modulith writes its own rows and cannot fill it; a generated column over the payload fails on events that are not JSON |
| A hand-written retry loop | The registry already tracks attempts and status; only the policy is ours |
| Swagger UI on the API port | The API port requires a bearer token, which a browser cannot supply to the UI |

## Consequences

- Every durable event must be a `DomainEvent` with a `context` component, and every handler a `@DurableEventListener`.
- A check made inside a lambda or a helper class is not seen by rule 7; the call goes in the use-case method or the method carries `@NoAuthorization` with a reason.
- Milestone 3 swaps `PermissionSource` and adds the profile status and email verification to the decision.
- The reference `notes` feature is backend-only; its screen, attachments and removal recipe arrive in [milestone 5](../product/roadmap/milestone-5-example-feature.md).

## Status

accepted. Resolves OQ-41 and the open choices of milestone 2. Extends [ADR 0027](0027-central-authorization-decision-point.md), [0031](0031-transactional-outbox-and-locked-jobs.md), [0039](0039-spring-modulith-module-boundaries-and-outbox.md), [0043](0043-http-api-conventions.md) and [0044](0044-authorization-enforcement-conventions.md), which stay accepted.

## Related

[Authorization](../architecture/identity/authorization.md), [communication](../architecture/communication.md), [persistence stack](../architecture/data/persistence-stack.md), [API conventions](../architecture/api/conventions.md), [architecture enforcement](../architecture/architecture-enforcement.md).
