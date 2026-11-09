# Milestone 1: Walking Skeleton

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** built and passing local runs on 2026-11-09; **not complete**, because by the [verification standard](../../engineering/testing/verification-standard.md) nothing counts until CI has run green. Locally: 27 backend tests (14 for the resource server, one for trace IDs in logs), 83 frontend unit tests, lint, type check and build, and a 4-test Playwright sign-in suite against real Keycloak, once with the applications on the host and once in the `apps` containers. The CI jobs for the frontend, the image build and scan, and the end-to-end test are written but have never run (the end-to-end job runs on pull requests labelled `e2e`, nightly and on demand). Windows only; macOS and Linux are untested. Gaps and deviations are listed under "Status against the criteria". See [implementation status](implementation-status.md).

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

## Status against the criteria

Recorded 2026-11-09. "Local" means a run on one Windows machine; none of it is verified by CI yet.

| Criterion | State |
|---|---|
| `pnpm bootstrap`, `infra:up`, `dev` on three operating systems from a clean clone | Local on Windows from this working copy. Not run from a fresh clone, and not on macOS or Linux |
| Compose services, realm by OpenTofu | Local; OQ-46 passes locally. Object storage is not part of this milestone (RustFS is gated in milestone 4) |
| Resource server rejects the six token cases and requires authentication everywhere | Local, with one test per case. Only the 401 side of the failure table exists; the 403 rows wait for milestones 2 and 3, and "Keycloak down, keys cached" is untested |
| Next.js sign-in, refresh, sign-out with callback, refresh-failure, logout and CSRF tests | Local (Vitest and Playwright) |
| `/api/*` proxy rules, each with a test | Local (Vitest) |
| `/me` through a server-rendered page and the browser proxy | Local (Playwright) |
| Health on the management port; `/healthz` | Local |
| One trace across Next.js and Spring, structured logs with the trace ID | Local: seen in Tempo. OTLP log export is **not** wired in either application (OQ-42, recorded gap) |
| Dockerfiles and the `apps` profile with a working sign-in | Local. The CI image scan has not run |
| Playwright sign-in test, setup guide | Local; the guide is rewritten for the fresh-clone path |
| All of the above in CI | **Open** |

### Deviations from the decisions

- The command is `pnpm bootstrap`, not `pnpm setup`, because pnpm has a built-in `setup` command.
- The frontend uses a temporary hand-written client for `GET /api/v1/me` instead of one generated from `openapi.json`, which arrives in milestone 2 and replaces it (owner decision of 2026-11-09).
- The web app's port is 3470 rather than 3000, because 3000 and the usual neighbours are often taken on developer machines.
- Keycloak's endpoints are written out in the BFF instead of discovered, because discovery rejects the split between the public issuer and the internal address.
