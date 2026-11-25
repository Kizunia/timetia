# 0008 Audience validation and deny-by-default

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

> **Tightened by [ADR 0047](0047-health-exposure-and-container-images.md)**: the health check moves to an internal management port, so no endpoint on the API port is public. This decision still holds.

## Context

Signature, issuer and expiry checks ([ADR 0007](0007-spring-security-resource-server.md)) would accept any valid token from the realm, including tokens meant for other clients. The definition does not mention audience or default access policy. The owner chose the recommended option, first labelled provisional; it was accepted on 2026-10-28 as part of the security baseline the owner delegated to the architect.

## Decision

Require the API's audience in access tokens (using a Keycloak audience mapper), and require authentication on every endpoint unless explicitly marked public. The only intended initial public endpoint is health.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Signature, issuer, expiry only | Accepts tokens issued for other clients |
| Leave audience undecided | Known security gap |

## Consequences

The realm must define the audience mapper, so the imported development realm ([ADR 0020](0020-keycloak-in-local-compose.md)) must include it. New endpoints are protected without extra work, and public ones are a deliberate act.

## Status

accepted. Resolves OQ-02. The security baseline around it (realm hardening, cookie and CSRF rules, behavior when signing keys are unavailable) is in [token validation](../architecture/identity/token-validation.md). Tightened by [ADR 0047](0047-health-exposure-and-container-images.md) (no public endpoint on the API port).

## Related

[Token validation](../architecture/identity/token-validation.md).
