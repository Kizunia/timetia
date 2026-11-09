# Authentication

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed, not verified in CI. Keycloak 26.8.2 on PostgreSQL 17.11 runs in `compose.yaml`; OpenTofu applies the realm (`infra/keycloak`); the Next.js server signs a user in with Authorization Code and PKCE, refreshes the session and signs out. A Playwright test against real Keycloak passed locally on 2026-11-09. Registration is enabled in the realm but no registration flow, email-verification enforcement or invitation exists yet (milestone 3).

Decisions: [ADR 0006](../../decisions/0006-keycloak-oidc-identity.md), [ADR 0007](../../decisions/0007-spring-security-resource-server.md), [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md), [ADR 0026](../../decisions/0026-nextjs-bff-session.md), [ADR 0025](../../decisions/0025-identity-and-authorization-data-split.md), [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md), [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md), [ADR 0045](../../decisions/0045-keycloak-operations.md).

## Responsibilities

- **Keycloak** authenticates users, issues tokens using OpenID Connect, sends identity email (invitation, verification, password reset) and owns the user lifecycle. Its data lives in its own PostgreSQL. The account lifecycle is in [user lifecycle](user-lifecycle.md).
- **The Next.js server** is the backend-for-frontend ([glossary](../../shared/glossary/backend-for-frontend.md)). It runs the login, holds the tokens, gives the browser only a session cookie and forwards browser API calls through one `/api` proxy ([frontend](../frontend.md)).
- **The Spring backend** is a [resource server](../../shared/glossary/resource-server.md): it accepts bearer access tokens and validates them ([token validation](token-validation.md)). It does not authenticate users or store passwords.
- **The user-administration module** calls the Keycloak Admin API to manage the user lifecycle. That requires a confidential service-account client, so the backend does hold one client secret for that purpose; the resource-server function itself still needs none.

## Sign-in flow

1. The browser asks the Next.js server for a protected page. With no session, the server starts the OpenID Connect Authorization Code flow with PKCE, as a confidential client.
2. Keycloak authenticates the user and returns to the Next.js callback. The server checks state, nonce and the PKCE verifier, and exchanges the code for tokens.
3. The server stores the session in an encrypted HttpOnly, Secure, SameSite cookie. The browser never receives a token.
4. The server calls Spring with the access token. The token must carry the backend's audience.
5. Expiring sessions are refreshed on the server, once at a time per request. If the refresh fails, the user signs in again.
6. Signing out ends the Keycloak session as well as the cookie.

Library and session store: `openid-client` plus a thin tested module, with the encrypted cookie as the store and a server-side store as the upgrade path for instant revocation ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)). State-changing requests are protected against CSRF: they must carry a matching `Origin` header and a required custom header ([ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md)).

## Local environment (decided, not implemented)

A local Keycloak runs in Docker Compose with its own PostgreSQL. Its realm is version-controlled and applied by OpenTofu, which runs as a one-shot container ([ADR 0045](../../decisions/0045-keycloak-operations.md)): a shared base (the Next.js client, the backend audience mapper, the least-privilege service-account client for administration, registration enabled, email verification handled by Kibootia) plus a local overlay (demo administrator and sample users). Secrets come from the generated `.env` ([ADR 0040](../../decisions/0040-local-development-topology-and-setup.md)); the configuration contains no real secrets, and production startup rejects demo data. The detailed realm contents are designed in milestone 1.

## Production exposure (decided, not implemented)

Keycloak's login pages are served on `auth.<domain>` through the TLS reverse proxy. The admin console is blocked at the proxy and reached through an SSH tunnel. A production overlay of the realm configuration contains no demo data. Production SMTP settings are Keycloak's, from any provider ([open questions](../../decisions/open-questions.md)).

## Realm hardening (decided, not implemented)

Brute-force detection, a password policy, access tokens of about five minutes and refresh-token rotation with a small reuse tolerance so that several tabs refreshing at once do not sign the user out.

## Failure behavior

Missing, invalid or expired tokens receive 401 with a Problem Details body ([API conventions](../api/conventions.md)). Tokens issued to service accounts rather than users are rejected ([ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md)).

## Security considerations

The resource server needs only Keycloak's public signing keys. The Next.js client secret and the administration service-account secret are secrets and follow [secrets management](../../operations/security/secrets-management.md). The attack surface is listed in the [threat model](../../operations/security/threat-model.md).
