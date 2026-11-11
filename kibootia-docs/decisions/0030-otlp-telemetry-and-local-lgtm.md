# 0030 OTLP telemetry with a local Grafana LGTM container

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** partly observed, not verified in CI. Spring and Next.js export traces over OTLP to the Grafana LGTM container, which `COMPOSE_PROFILES=observability` in `.env` starts by default; one trace covering both applications was seen in Tempo on 2026-11-09. Both applications print JSON console logs with the trace ID (Spring's is tested). **Not wired:** OTLP log export (OQ-42). `kibootia.observability.enabled` switches the Spring export off; `KIBOOTIA_OBSERVABILITY_ENABLED=false` does the same for Next.js.

## Context

The original [observability baseline](../operations/monitoring/observability-baseline.md) limited the foundation to basic logging, request correlation and health checks, and deferred metrics, tracing and dashboards. The owner now wants a complete local observability stack, but with one telemetry path and without duplicate collectors.

## Decision

- The application emits logs, metrics and traces over **OTLP only**, using Spring Boot's OpenTelemetry starter with Micrometer. Which backend receives the data is a deployment choice.
- Locally, **one Grafana LGTM all-in-one development container** receives the data and provides dashboards. It starts by default with `docker compose up`, and can be switched off with a property and a Compose profile.
- Production points the same OTLP settings at a managed backend, or at a self-hosted collector with Prometheus, Tempo, Loki and Grafana (a recipe).
- Logs always also go to the console as structured JSON carrying trace and span IDs, so they work without the stack.
- Health uses Actuator liveness and readiness, separate from telemetry.
- The Next.js server passes W3C trace context to Spring.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Separate collector, Prometheus, Tempo, Loki and Grafana containers locally | Mirrors production, but more configuration, memory and start-up time |
| Classic pull model (Prometheus scrapes Actuator, a log shipper feeds Loki) | Three separate data paths |
| Opt-in profile | The fresh-clone target is to see the infrastructure working together |

## Consequences

The all-in-one image is for development only. OTLP log export in the Spring Boot version in use needs extra wiring that must be verified when it is built.

## Status

accepted. Replaces the scope of the draft observability baseline; that document is updated in the same change.

## Related

[Observability baseline](../operations/monitoring/observability-baseline.md), [technology catalogue](../technology/README.md).
