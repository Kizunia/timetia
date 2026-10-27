# Token Validation and Default Access Policy

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. Resource-server starters are in `pom.xml`; no issuer property or security configuration exists.

## Required checks (owner decision, [ADR 0007](../../decisions/0007-spring-security-resource-server.md))

On every protected request the backend validates the access token's:

1. **Signature**, against the identity provider's published keys.
2. **Issuer**, against the configured Keycloak realm.
3. **Expiry**.

Spring Security's resource-server support provides these checks when configured with the issuer. Configuring it is part of [milestone 3](../../product/roadmap/milestone-3-security-baseline.md).

A valid token does not authorize every operation; see [authorization](authorization.md).

## Provisional additions ([ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md), draft)

- **Audience**: tokens must carry this API's audience, so tokens issued for other clients in the realm are refused. It requires an audience mapper in the Keycloak realm.
- **Deny by default**: every endpoint requires authentication unless explicitly marked public. Initially the only intended public endpoint is the health check, and its final exposure is an open point ([observability baseline](../../operations/monitoring/observability-baseline.md)).

## Failure cases

| Case | Intended outcome |
|---|---|
| No token on a protected endpoint | Authentication failure response (shape in [API conventions](../api/conventions.md), open) |
| Bad signature, wrong issuer, expired | Authentication failure |
| Valid token, insufficient role | Authorization failure |
| Keycloak unreachable | Not decided: behavior of key retrieval failures is an open point |

## Testing

Each failure row needs an automated test that runs in CI ([verification standard](../../engineering/testing/verification-standard.md)). None exists.
