# Token Validation and Default Access Policy

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed, not verified in CI. `platform/security` validates signature, expiry, issuer, audience and rejects service-account tokens; deny-by-default, stateless, no CORS; 401 and 403 from the filter chain are Problem Details. `ResourceServerTests` (14 tests) cover each rejection case and passed locally on 2026-11-09. The service-account check was confirmed against a real Keycloak token (it carries `client_id` and a `service-account-` username). **Not yet implemented:** the 403 rows (permission, disabled or deleted profile) wait for milestones 2 and 3, and the "Keycloak down, keys cached" rows are untested (the key cache is the decoder's default, about five minutes).

## Required checks (owner decision, [ADR 0007](../../decisions/0007-spring-security-resource-server.md))

On every protected request the backend validates the access token's:

1. **Signature**, against the identity provider's published keys.
2. **Issuer**, against the configured Keycloak realm.
3. **Expiry**.

Spring Security's resource-server support provides these checks when configured with the issuer. Configuring it is part of [milestone 1](../../product/roadmap/milestone-1-walking-skeleton.md).

A valid token does not authorize every operation; see [authorization](authorization.md).

## Additional rules ([ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md), accepted)

- **Audience**: tokens must carry this API's audience, so tokens issued for other clients in the realm are refused. It requires an audience mapper in the Keycloak realm.
- **Deny by default**: every endpoint requires authentication unless explicitly marked public. The API port has **no public endpoints**: health checks run on a separate internal management port ([ADR 0047](../../decisions/0047-health-exposure-and-container-images.md)).
- **User tokens only**: tokens issued to service accounts rather than users are rejected ([ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md)).
- **No CORS**: browsers never call the backend directly ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)). The backend may sit on a private network behind the Next.js server and may also serve non-browser clients that send bearer tokens.
- **Signing keys**: if Keycloak's keys cannot be fetched, the backend uses the keys it has cached; with none cached it refuses requests (fails closed).
- **Production guard**: startup fails if development defaults are detected (demo realm, demo passwords, development client secrets, seed data).

## Failure cases

| Case | Intended outcome |
|---|---|
| No token on a protected endpoint | 401 with a Problem Details body ([API conventions](../api/conventions.md)) |
| Bad signature, wrong issuer, wrong audience, expired | 401 |
| Token issued to a service account | 401 |
| Valid token, insufficient permission | 403 |
| Valid token, profile disabled or deleted | 403 |
| Keycloak unreachable, keys cached | Requests continue with the cached keys |
| Keycloak unreachable, no keys cached | Requests are refused |

## Testing

Each failure row needs an automated test that runs in CI ([verification standard](../../engineering/testing/verification-standard.md)). None exists.
