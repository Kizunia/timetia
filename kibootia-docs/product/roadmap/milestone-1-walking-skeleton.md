# Milestone 1: Walking Skeleton

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. Observed today: MySQL and Adminer in `compose.yaml`; nothing else in the scope below.

## Scope

The thinnest end-to-end chain that works on a fresh clone: a user signs in through the Next.js server, which calls a protected Spring endpoint, and the request shows up in logs and traces. Decisions: [ADR 0020](../../decisions/0020-keycloak-in-local-compose.md), [ADR 0026](../../decisions/0026-nextjs-bff-session.md), [ADR 0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md), [ADR 0040](../../decisions/0040-local-development-topology-and-setup.md), [ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md), [ADR 0045](../../decisions/0045-keycloak-operations.md), [ADR 0047](../../decisions/0047-health-exposure-and-container-images.md).

## Completion criteria (approved by the owner on 2026-11-08)

- `pnpm bootstrap`, `pnpm infra:up` and `pnpm dev` work on Windows, macOS and Linux from a clean clone ([ADR 0040](../../decisions/0040-local-development-topology-and-setup.md)).
- Compose starts MySQL, Keycloak on its own PostgreSQL, a one-shot OpenTofu step that applies the realm (base plus local overlay), Mailpit, Adminer and the Grafana LGTM container. The OpenTofu Keycloak provider works with the pinned Keycloak, which runs on the pinned PostgreSQL (check OQ-46).
- The Spring resource server rejects missing, expired, wrongly signed, wrong-issuer, wrong-audience and service-account tokens, and requires authentication on every endpoint; the API port has no public endpoint. Each case has an automated test ([ADR 0008](../../decisions/0008-audience-validation-and-deny-by-default.md), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md)).
- The Next.js BFF signs a user in, refreshes the session and signs out, with tests for the callback, refresh failure, logout and CSRF cases ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)).
- The generic `/api/*` proxy attaches the token, enforces the Origin and custom-header rule, streams bodies, passes trace context and strips cookies, each covered by a test ([ADR 0042](../../decisions/0042-bff-api-proxy-and-csrf.md)).
- A `/me` endpoint returns the signed-in user's identity through both a server-rendered page and the browser proxy.
- Actuator health runs on the internal management port; the Next.js server has `/healthz` ([ADR 0047](../../decisions/0047-health-exposure-and-container-images.md)).
- A request is visible as one trace across Next.js and Spring, with structured logs carrying the trace ID; OTLP log export is wired or the gap is recorded (check OQ-42).
- The first Dockerfiles exist and the `apps` Compose profile runs both applications in containers with a working sign-in; the issuer handling is covered by a test (check OQ-45).
- A Playwright sign-in test passes, and the local setup guide describes the whole fresh-clone path.
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).
