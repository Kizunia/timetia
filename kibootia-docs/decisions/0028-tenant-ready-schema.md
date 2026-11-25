# 0028 Tenant-ready schema

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Context

The project definition keeps a path toward tenant-aware data access and authorization, but excludes a tenant-management subsystem. Retrofitting isolation after data has spread across tables, queries, jobs, caches and stored files is expensive and risky. MySQL has no built-in row-level security, so isolation has to be enforced in the application.

## Decision

Kibootia is **tenant-ready but runs a single default tenant**.

- A per-request tenant context resolves to the default tenant.
- Every foundation-owned table carries `tenant_id`, enforced by a Hibernate `@TenantId` discriminator on a shared schema. Native queries bypass that filter, so they need explicit review and tests.
- Tenant facts are passed to the authorization decision point ([ADR 0027](0027-central-authorization-decision-point.md)).
- Stored object keys are prefixed with the tenant, and events and jobs carry the tenant.
- Automated cross-tenant isolation tests run in CI.

Not included: a tenant management UI, organization onboarding, live tenant resolution (token claim or subdomain), Keycloak Organizations. These stay future or recipe tier, so the non-goal "full tenant-management subsystem" still holds.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| No preparation | Leaves the full, risky retrofit to every application that needs tenants |
| Seams only, no columns | Cheaper, but the foundation's own tables still need migration and query review when tenancy arrives |
| Tenant-aware by default | Every developer and demo deals with tenants; conflicts with the scope above |

## Consequences

A constant column and a resolver cost little; applications that stay single-tenant carry a column they never use. Schema-per-tenant or database-per-tenant is left to applications.

## Status

accepted. Resolves the tenant-ready-columns part of OQ-27.

## Related

[Transactions and data ownership](../architecture/data/transactions-and-data-ownership.md), [persistence stack](../architecture/data/persistence-stack.md).
