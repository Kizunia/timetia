# Authentication

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. No Keycloak service is in `compose.yaml` and no issuer is configured.

Decisions: [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0007](../../decisions/0007-spring-security-resource-server.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md).

## Responsibilities

- **Keycloak** authenticates users and issues tokens using OpenID Connect.
- **The backend** is a [resource server](../../shared/glossary/resource-server.md): it accepts bearer access tokens and validates them ([token validation](token-validation.md)). It does not authenticate users or store passwords.
- **The frontend** takes users through login and presents the resulting state. Its flow is not yet decided.

## Local environment (decided, not implemented)

A local Keycloak runs in Docker Compose with a version-controlled, imported realm containing a development client, sample users and roles. The realm file must contain no real secrets. The realm's contents are not yet designed.

## Frontend login flow (open)

Deferred to [milestone 6](../../product/roadmap/milestone-6-frontend-integration.md). The options discussed, none chosen:

| Option | Trade-off |
|---|---|
| Next.js server holds the session and tokens (Authorization Code + PKCE), browser keeps only an HttpOnly cookie | Tokens not exposed to browser scripts; needs a server-side session and refresh layer |
| Browser holds tokens and calls the backend directly | Simpler path; tokens exposed to any script injection |

Recorded in [open questions](../../decisions/open-questions.md). Whatever is chosen needs its own ADR, and affects CORS and CSRF policy.

## Failure behavior

Responses for missing, invalid or expired tokens are part of [API conventions](../api/conventions.md) (open).

## Security considerations

Only Keycloak's public signing keys are needed by the backend. No client secret should be necessary for the resource server itself. Anything a confidential frontend client needs is a secret and follows [secrets management](../../operations/security/secrets-management.md).
