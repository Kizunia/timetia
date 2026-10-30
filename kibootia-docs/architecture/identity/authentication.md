# Authentication

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No Keycloak service is in `compose.yaml`, no issuer is configured and there is no `frontend/` directory.

Decisions: [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0007](../../decisions/0007-spring-security-resource-server.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md), [ADR 0026](../../decisions/0026-nextjs-bff-session.md), [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md).

## Responsibilities

- **Keycloak** authenticates users, issues tokens using OpenID Connect and owns the user lifecycle.
- **The Next.js server** is the backend-for-frontend ([glossary](../../shared/glossary/backend-for-frontend.md)). It runs the login, holds the tokens and gives the browser only a session cookie.
- **The Spring backend** is a [resource server](../../shared/glossary/resource-server.md): it accepts bearer access tokens and validates them ([token validation](token-validation.md)). It does not authenticate users or store passwords.
- **The user-administration module** calls the Keycloak Admin API to manage the user lifecycle. That requires a confidential service-account client, so the backend does hold one client secret for that purpose; the resource-server function itself still needs none.

## Sign-in flow

1. The browser asks the Next.js server for a protected page. With no session, the server starts the OpenID Connect Authorization Code flow with PKCE, as a confidential client.
2. Keycloak authenticates the user and returns to the Next.js callback. The server checks state, nonce and the PKCE verifier, and exchanges the code for tokens.
3. The server stores the session in an encrypted HttpOnly, Secure, SameSite cookie. The browser never receives a token.
4. The server calls Spring with the access token. The token must carry the backend's audience.
5. Expiring sessions are refreshed on the server, once at a time per request. If the refresh fails, the user signs in again.
6. Signing out ends the Keycloak session as well as the cookie.

Library and session store: `openid-client` plus a thin tested module, with the encrypted cookie as the store and a server-side store as the upgrade path for instant revocation ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)). State-changing requests are protected against CSRF.

## Local environment (decided, not implemented)

A local Keycloak runs in Docker Compose with a version-controlled, imported realm containing the Next.js client, the backend audience mapper, the service-account client for administration, a demo administrator and sample users. The realm file must contain no real secrets and is rejected by production startup. The realm's contents are not yet designed.

## Realm hardening (decided, not implemented)

Brute-force detection, a password policy, access tokens of about five minutes and refresh-token rotation with a small reuse tolerance so that several tabs refreshing at once do not sign the user out.

## Failure behavior

Responses for missing, invalid or expired tokens are part of [API conventions](../api/conventions.md) (open).

## Security considerations

The resource server needs only Keycloak's public signing keys. The Next.js client secret and the administration service-account secret are secrets and follow [secrets management](../../operations/security/secrets-management.md). The attack surface is listed in the [threat model](../../operations/security/threat-model.md).
