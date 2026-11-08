# 0026 Next.js as backend-for-frontend with an encrypted cookie session

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. There is no `frontend/` directory.

> **Extended by [ADR 0042](0042-bff-api-proxy-and-csrf.md)**, which re-examined this decision against RFC 10017, reaffirmed it, and adds the generic API proxy and the CSRF rule.

## Context

The frontend login flow was open as OQ-20 ([authentication](../architecture/identity/authentication.md) listed two options). The choice decides whether the browser ever holds access tokens, whether the API needs CORS, and how Next.js must be deployed.

## Decision

The **Next.js server is the backend-for-frontend (BFF)**. It is a confidential Keycloak client that runs the OpenID Connect Authorization Code flow with PKCE and holds the tokens on the server. The browser receives only an HttpOnly, Secure, SameSite session cookie. Next.js server code calls the Spring backend with a bearer access token, so Spring remains a stateless resource server ([ADR 0007](0007-spring-security-resource-server.md)).

- **Library:** `openid-client` plus a thin, fully tested Kibootia module for login, callback, refresh, logout and CSRF protection. Tests cover state, nonce and PKCE validation, the refresh failure path (which forces sign-in again), logout and CSRF.
- **Session store:** the session lives in an encrypted HttpOnly cookie, chunked if large. Refresh is single-flight per request. Keycloak's refresh-token reuse tolerance is set small so rotation does not log users out when several tabs refresh at once. A server-side store such as Redis is the documented upgrade path for instant revocation.
- **Consequences for Spring:** no CORS for browser traffic; Spring may sit on a private network behind the BFF.
- **Consequences for Next.js:** it runs as a Node server (not static-only hosting); state-changing requests are protected against CSRF; logout also ends the Keycloak session; trace context is passed on to Spring.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Tokens held in the browser, calling Spring directly | Tokens are exposed to any script injection; needs CORS; the weakest pattern in current guidance for browser-based OAuth applications |
| Spring Security as the BFF with a session store | Makes Spring session-holding and partly reverses ADR 0007 |
| Auth.js | Joined Better Auth on 2025-09-22 and now receives security patches only |
| Better Auth | Keeps its own user and session tables, duplicating Keycloak's role ([ADR 0025](0025-identity-and-authorization-data-split.md)) |
| Server-side session store from the start | Adds Redis to the stack for a benefit most applications do not need first |

## Consequences

An extra hop for every API call, and about a few hundred lines of security-critical code owned by Kibootia, which must stay well tested.

## Status

accepted. Resolves OQ-20. Reaffirmed and extended by [ADR 0042](0042-bff-api-proxy-and-csrf.md).

## Related

[Authentication](../architecture/identity/authentication.md), [ADR 0038](0038-frontend-stack.md), [ADR 0008](0008-audience-validation-and-deny-by-default.md).
