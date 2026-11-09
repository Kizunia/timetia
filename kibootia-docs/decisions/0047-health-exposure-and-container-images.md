# 0047 Health exposure and container images

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** partly observed, not verified in CI. Actuator exposes only `health` with liveness and readiness (MySQL only) on port 8090 bound to loopback; `ResourceServerTests` check it answers without a token there and not on the API port. `backend/Dockerfile` (Maven build, Temurin 25 JRE, layered jar, non-root user) and `frontend/Dockerfile` (standalone output on Node 24, non-root) built, and both containers became healthy locally on 2026-11-09. **Not done:** the CI image build and vulnerability scan (the `images` job is written but has not run), so the image scan part of OQ-44 is open.

## Context

OQ-32 left open the health endpoint contract and its exposure, and how the backend and Next.js images are built. [ADR 0008](0008-audience-validation-and-deny-by-default.md) named the health check as the only intended public endpoint. [ADR 0040](0040-local-development-topology-and-setup.md) brings container images forward to the first `apps` Compose profile.

## Decision

**Health.**

- Spring Boot Actuator listens on a **separate internal management port** that the reverse proxy never exposes.
- Only the `health` endpoint is enabled, with liveness and readiness groups. Other Actuator endpoints are disabled.
- Readiness checks MySQL only. Keycloak is not included because signing keys are cached ([token validation](../architecture/identity/token-validation.md)); object storage is not included because an outage affects only file features.
- The Next.js server has a minimal `/healthz` route for its container check.
- The API port therefore has **no public endpoints at all**.

**Images.** Explicit multi-stage Dockerfiles for both applications:

- backend: a Maven build stage, then a slim JRE stage on the chosen JDK ([ADR 0048](0048-java-25-baseline-on-temurin.md)) with Spring Boot's layered jar and a non-root user;
- Next.js: `output: "standalone"` on a slim Node LTS image, non-root.

CI builds and scans both images on every pull request. Kibootia publishes no images; forks build and publish their own.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Health on the API port, public, without details | Probe reachable from the internet |
| Health on the API port, authenticated | Container health checks would need credentials |
| Spring Boot buildpacks for the backend | Less transparent, base image chosen by the buildpack, and Next.js would still need a Dockerfile |
| Jib for the backend | Same mismatch between the two applications |

## Consequences

- Compose health checks and the deployment use the management port.
- Production metrics travel only over OTLP ([ADR 0030](0030-otlp-telemetry-and-local-lgtm.md)); no metrics endpoint is scraped.

## Status

accepted. Resolves OQ-32. Tightens [ADR 0008](0008-audience-validation-and-deny-by-default.md): no endpoint on the API port is public, including health. ADR 0008 stays accepted.

## Related

[Container deployment](../operations/deployment/container-deployment.md), [observability baseline](../operations/monitoring/observability-baseline.md).
