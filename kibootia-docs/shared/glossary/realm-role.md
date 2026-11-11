# Realm Role

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (definition).

A role defined at the realm level in Keycloak, shared by all clients in that realm. In access tokens issued by Keycloak realm roles appear in the `realm_access.roles` claim. Kibootia does not use them as its role source: roles live in Kibootia's database ([ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), which superseded [ADR 0010](../../decisions/0010-keycloak-realm-roles-as-role-source.md)). Contrast with client roles, which are scoped to one Keycloak client.
