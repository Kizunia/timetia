# Transactions and Data Ownership

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Transactions (owner direction)

Transaction boundaries align with application operations that need atomic change, and are placed deliberately, not spread across every layer. In the layering of [layers and boundaries](../layers-and-boundaries.md) that points at application use cases. A change that must publish an event records it in the outbox (Spring Modulith's event publication registry) in the same transaction ([ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md)). An auditable change writes its audit entry in the same transaction ([ADR 0046](../../decisions/0046-audit-log-design.md)).

## Data ownership

Each [feature](../../shared/glossary/feature.md) owns its persistence implementation inside its module's private `persistence/` package ([package structure](../../engineering/conventions/package-structure.md)). A feature must not read or write another feature's private tables or repositories; it uses that feature's public contract. This is checked by Spring Modulith's module verification ([architecture enforcement](../architecture-enforcement.md)).

## Tenant isolation

The schema is tenant-ready with a single default tenant ([ADR 0028](../../decisions/0028-tenant-ready-schema.md)). Isolation is enforced in data access and in the authorization decision, never by filtering in the frontend:

- every foundation-owned table carries `tenant_id`, enforced by a Hibernate discriminator;
- native queries bypass that discriminator and need explicit tenant conditions, review and tests;
- background jobs and events carry the tenant, and stored object keys are prefixed with it;
- automated cross-tenant isolation tests run in CI.

Organization onboarding, administration and a tenant-management subsystem remain out of scope.

## Related

[Persistence stack](persistence-stack.md), [authorization](../identity/authorization.md).
