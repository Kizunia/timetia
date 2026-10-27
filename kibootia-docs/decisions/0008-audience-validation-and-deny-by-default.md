# 0008 Audience validation and deny-by-default

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

Signature, issuer and expiry checks ([ADR 0007](0007-spring-security-resource-server.md)) would accept any valid token from the realm, including tokens meant for other clients. The definition does not mention audience or default access policy. The owner chose the recommended option, labelled provisional.

## Decision

Provisional: require the API's audience in access tokens (using a Keycloak audience mapper), and require authentication on every endpoint unless explicitly marked public. The only intended initial public endpoint is health.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Signature, issuer, expiry only | Accepts tokens issued for other clients |
| Leave audience undecided | Known security gap |

## Consequences

The realm must define the audience mapper, so the imported development realm ([ADR 0020](0020-keycloak-in-local-compose.md)) must include it. New endpoints are protected without extra work, and public ones are a deliberate act.

## Status

draft (provisional; listed in [open questions](open-questions.md))

## Related

[Token validation](../architecture/identity/token-validation.md).
