# 0006 Keycloak with OpenID Connect for identity

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. No Keycloak configuration exists in the repository.

## Context

Consuming applications need authentication without each building a user store and login mechanism. Source: project definition, section 6.1.

## Decision

Keycloak is the intended identity provider, using OpenID Connect for identity integration and token issuance. Kibootia does not store credentials.

## Alternatives considered

The project definition names no alternatives; none were evaluated in the documentation interview.

## Consequences

An operational dependency (Keycloak) for development and production. Local setup is addressed in [ADR 0020](0020-keycloak-in-local-compose.md). The frontend login flow is not decided by this ADR.

## Status

accepted

## Related

[Authentication](../architecture/identity/authentication.md).
