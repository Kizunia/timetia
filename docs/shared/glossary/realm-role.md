# Realm Role

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (definition).

A role defined at the realm level in Keycloak, shared by all clients in that realm. In access tokens issued by Keycloak realm roles appear in the `realm_access.roles` claim. The proposed use as Kibootia's role source is provisional: [ADR 0010](../../decisions/0010-keycloak-realm-roles-as-role-source.md). Contrast with client roles, which are scoped to one Keycloak client.
