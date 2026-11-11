# 0042 Generic backend-for-frontend API proxy and CSRF protection

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed, not verified in CI. `frontend/src/lib/bff/proxy.ts` implements the fixed upstream, the Origin and `X-Kibootia-Request` rule, token attachment, streaming in both directions, trace context from the server's own span and the stripping of cookies and hop-by-hop headers; each rule has a unit test, and the browser path is covered by a Playwright test. Passed locally on 2026-11-09. An OPTIONS request gets 405, so no CORS preflight is ever answered.

## Context

[ADR 0026](0026-nextjs-bff-session.md) makes the Next.js server the backend-for-frontend (BFF): the browser holds only a session cookie and never a token. It did not say how code running in the browser (client-side data fetching, form submissions, uploads) reaches Spring, and the [threat model](../operations/security/threat-model.md) left the CSRF mechanism open.

At the owner's request the premise "the browser never holds the token" was re-examined against current guidance before building on it. RFC 10017, *OAuth 2.0 for Browser-Based Apps* (IETF Best Current Practice, August 2026), presents its architecture patterns in decreasing order of security: the BFF first, "strongly recommended for business applications, sensitive applications"; a token-mediating backend "less secure than a BFF"; and a browser-based OAuth client holding tokens, "vulnerable to all attack scenarios" it discusses. In that document a BFF is a proxy that attaches the access token to API calls.

## Decision

**ADR 0026 stands**, and the Next.js server remains the BFF.

- **One generic proxy route**, `/api/*` on the Next.js server, forwards to Spring's `/api/*`. It attaches the access token from the session, enforces the CSRF rule below, streams request and response bodies (uploads and downloads), passes W3C trace context, and strips cookies and hop-by-hop headers.
- **Server-rendered code** (server components and route handlers) calls Spring directly with the same generated, typed client ([ADR 0043](0043-http-api-conventions.md)), using the token from the session.
- Next.js Server Actions are **not** used for API calls. All API logic and authorization stay in Spring.
- **CSRF:** every request that is not GET or HEAD must carry an `Origin` header matching Kibootia's own origin and a required custom header (for example `X-Kibootia-Request`). Browsers cannot send a custom header to another site without a CORS preflight, which Kibootia never grants. Together with the SameSite session cookie this blocks cross-site request forgery. The generated client adds the header.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Spring Cloud Gateway as the BFF | Battle-tested Spring Security login code, but a third deployable, a server-side session store, and Next.js would have to pass the cookie through for server rendering |
| Tokens held in the browser, calling Spring with CORS | The pattern RFC 10017 ranks least secure; reintroduces CORS |
| Per-feature Next.js route handlers and Server Actions | Duplicates the API surface in Next.js; Server Actions have a small default body limit that suits uploads poorly |
| A mix of proxy reads and Server Action writes | Two patterns to learn and secure |
| Synchronizer CSRF token | Token plumbing in every form and call with no benefit over the chosen rule when CORS is never granted |

## Consequences

- Every Spring `/api` endpoint is reachable from the browser through the proxy; this is acceptable because Spring authorizes every call ([ADR 0008](0008-audience-validation-and-deny-by-default.md), [ADR 0027](0027-central-authorization-decision-point.md)).
- The proxy is security-critical and needs its own tests: token attachment, refresh failure, CSRF acceptance and rejection, streaming, and header stripping.
- One extra network hop per browser API call.

## Status

accepted. Extends [ADR 0026](0026-nextjs-bff-session.md), which stays accepted. Settles the CSRF choice left open in the threat model.

## Related

[Frontend architecture](../architecture/frontend.md), [authentication](../architecture/identity/authentication.md), [RFC 10017](https://datatracker.ietf.org/doc/rfc10017/) (IETF datatracker).
