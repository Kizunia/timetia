# Transactions and Data Ownership

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Transactions (owner direction)

Transaction boundaries align with application operations that need atomic change, and are placed deliberately, not spread across every layer. In the layering of [layers and boundaries](../layers-and-boundaries.md) that points at application use cases. A change that must publish an event writes the event to the outbox in the same transaction ([ADR 0031](../../decisions/0031-transactional-outbox-and-locked-jobs.md)).

## Data ownership

Each [feature](../../shared/glossary/feature.md) owns its persistence implementation. A feature must not read or write another feature's private tables or repositories; it uses that feature's public contract. This is intended to be checked by [architecture rules](../architecture-enforcement.md) where practical.

## Tenant isolation

The schema is tenant-ready with a single default tenant ([ADR 0028](../../decisions/0028-tenant-ready-schema.md)). Isolation is enforced in data access and in the authorization decision, never by filtering in the frontend:

- every foundation-owned table carries `tenant_id`, enforced by a Hibernate discriminator;
- native queries bypass that discriminator and need explicit tenant conditions, review and tests;
- background jobs and events carry the tenant, and stored object keys are prefixed with it;
- automated cross-tenant isolation tests run in CI.

Organization onboarding, administration and a tenant-management subsystem remain out of scope.

## Related

[Persistence stack](persistence-stack.md), [authorization](../identity/authorization.md).
