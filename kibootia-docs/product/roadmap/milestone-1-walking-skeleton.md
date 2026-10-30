# Milestone 1: Walking Skeleton

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. Observed today: MySQL and Adminer in `compose.yaml`; nothing else in the scope below.

## Scope (owner decision)

The thinnest end-to-end chain that works on a fresh clone: a user signs in through the Next.js server, which calls a protected Spring endpoint, and the request shows up in logs and traces. Later milestones fill it in.

## Completion criteria (proposed, not yet approved)

- Compose starts MySQL, Keycloak with an imported development realm ([ADR 0020](../../decisions/0020-keycloak-in-local-compose.md)) and the Grafana LGTM container ([ADR 0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md)).
- The Spring resource server rejects missing, expired, wrongly signed, wrong-issuer and wrong-audience tokens, and requires authentication on every endpoint unless explicitly public ([ADR 0007](../../decisions/0007-spring-security-resource-server.md), [ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md)). Each case has an automated test.
- The Next.js BFF signs a user in, refreshes the session and signs out ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)), with tests for the callback, refresh failure, logout and CSRF cases.
- A `/me` endpoint returns the signed-in user's identity.
- Health endpoints exist, with the exposure policy documented.
- A request is visible as one trace across Next.js and Spring, with structured logs carrying the trace ID.
- A Playwright sign-in test passes, and the local setup guide describes the whole fresh-clone path.
- All of the above pass in CI.
