# Identity

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned (resource-server starters in `pom.xml`; no security configuration).

| Item | Summary |
|---|---|
| [authentication.md](authentication.md) | Keycloak with OpenID Connect; backend as resource server; frontend flow deferred |
| [token-validation.md](token-validation.md) | What the backend checks on every token; default access policy |
| [authorization.md](authorization.md) | Role-based authorization, role source, extension path |

Related decisions: ADRs [0006](../../decisions/0006-keycloak-oidc-identity.md), [0007](../../decisions/0007-spring-security-resource-server.md), [0008](../../decisions/0008-audience-validation-and-deny-by-default.md), [0009](../../decisions/0009-role-based-authorization-first.md), [0010](../../decisions/0010-keycloak-realm-roles-as-role-source.md), [0020](../../decisions/0020-keycloak-in-local-compose.md).
