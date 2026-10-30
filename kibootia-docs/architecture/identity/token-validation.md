# Token Validation and Default Access Policy

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. Resource-server starters are in `pom.xml`; no issuer property or security configuration exists.

## Required checks (owner decision, [ADR 0007](../../decisions/0007-spring-security-resource-server.md))

On every protected request the backend validates the access token's:

1. **Signature**, against the identity provider's published keys.
2. **Issuer**, against the configured Keycloak realm.
3. **Expiry**.

Spring Security's resource-server support provides these checks when configured with the issuer. Configuring it is part of [milestone 1](../../product/roadmap/milestone-1-walking-skeleton.md).

A valid token does not authorize every operation; see [authorization](authorization.md).

## Additional rules ([ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md), accepted)

- **Audience**: tokens must carry this API's audience, so tokens issued for other clients in the realm are refused. It requires an audience mapper in the Keycloak realm.
- **Deny by default**: every endpoint requires authentication unless explicitly marked public. Initially the only intended public endpoint is the health check, and its final exposure is an open point ([observability baseline](../../operations/monitoring/observability-baseline.md)).
- **No CORS**: browsers never call the backend directly ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)). The backend may sit on a private network behind the Next.js server and may also serve non-browser clients that send bearer tokens.
- **Signing keys**: if Keycloak's keys cannot be fetched, the backend uses the keys it has cached; with none cached it refuses requests (fails closed).
- **Production guard**: startup fails if development defaults are detected (demo realm, demo passwords, development client secrets, seed data).

## Failure cases

| Case | Intended outcome |
|---|---|
| No token on a protected endpoint | Authentication failure response (shape in [API conventions](../api/conventions.md), open) |
| Bad signature, wrong issuer, wrong audience, expired | Authentication failure |
| Valid token, insufficient permission | Authorization failure |
| Keycloak unreachable, keys cached | Requests continue with the cached keys |
| Keycloak unreachable, no keys cached | Requests are refused |

## Testing

Each failure row needs an automated test that runs in CI ([verification standard](../../engineering/testing/verification-standard.md)). None exists.
