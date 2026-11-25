# 0007 Spring Security resource server with signature, issuer and expiry validation

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed (starters only). No security configuration exists.

## Context

The backend must protect requests and must not trust tokens blindly. Source: project definition, section 6.2.

## Decision

Spring Security protects the backend as an OAuth2 [resource server](../shared/glossary/resource-server.md). It validates, at minimum, token signature, issuer and expiry. A valid token alone does not authorize every operation.

## Alternatives considered

None recorded.

## Consequences

The Keycloak issuer must be configured per environment. Additional checks (audience) are in [ADR 0008](0008-audience-validation-and-deny-by-default.md).

## Status

accepted

## Related

[Token validation](../architecture/identity/token-validation.md).
