# Transactions and Data Ownership

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Transactions (owner direction)

Transaction boundaries align with application operations that need atomic change, and are placed deliberately, not spread across every layer. In the layering of [layers and boundaries](../layers-and-boundaries.md) that points at application use cases.

## Data ownership

Each [feature](../../shared/glossary/feature.md) owns its persistence implementation. A feature must not read or write another feature's private tables or repositories; it uses that feature's public contract. This is intended to be checked by [architecture rules](../architecture-enforcement.md) where practical.

## Tenant path

Kibootia keeps a path toward tenant-aware data access and authorization, but organization onboarding, administration and a tenant-management subsystem are out of scope. When tenancy arrives, isolation must be enforced in data access and authorization, not merely by filtering in the frontend. How the starter prepares for this (for example reserved columns) is undecided ([open questions](../../decisions/open-questions.md)).

## Related

[Persistence stack](persistence-stack.md), [authorization](../identity/authorization.md).
