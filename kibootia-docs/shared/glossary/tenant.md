# Tenant

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (definition).

One customer organization served by an application. Kibootia runs a single default tenant but every foundation table carries a tenant identifier, so isolation can be enforced when more tenants are introduced. Decision: [ADR 0028](../../decisions/0028-tenant-ready-schema.md).
