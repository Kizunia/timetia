# Observability Baseline

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** partly observed, not verified in CI. Both applications export traces over OTLP to Grafana LGTM; a single trace was seen in Tempo covering the Next.js proxy call and the Spring request, with the Spring span as a child of the Next.js `fetch` span (locally, 2026-11-09). Both print JSON logs carrying the trace ID. **Gap:** OTLP log export is not wired, in Spring or Next.js, so logs reach Grafana only through the console (OQ-42). Metrics from Spring are exported over OTLP; nothing is scraped.

Decision: [ADR 0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md).

## Scope (owner decision of 2026-10-28)

The foundation provides logs, metrics, traces, request correlation and health checks, and a local stack to see them. This replaces the earlier scope of basic logging, correlation and health only.

## Design

```mermaid
flowchart LR
    be[Spring backend] -->|OTLP logs, metrics, traces| lgtm[Grafana LGTM container<br/>local only]
    fe[Next.js server] -->|OTLP, trace context| lgtm
    fe -->|traceparent| be
    be --> console[Console JSON logs]
```

| Signal | How |
|---|---|
| Logs | Structured JSON on the console always, carrying trace and span IDs; also sent over OTLP when the stack is on |
| Metrics | Micrometer, exported over OTLP |
| Traces | OpenTelemetry, with W3C trace context passed from the Next.js server to Spring, through the outbox and into jobs |
| Correlation | The trace ID doubles as the request correlation ID, visible in logs and available to callers |
| Health | Actuator liveness and readiness on a separate internal management port; readiness checks MySQL only. The Next.js server has `/healthz` ([ADR 0047](../../decisions/0047-health-exposure-and-container-images.md)) |

The application emits OTLP only. Where it goes is deployment configuration, so there is one telemetry path and no duplicate collectors.

## Local and production

- **Locally:** one Grafana LGTM all-in-one development container starts with `docker compose up`. It can be switched off with a property and a Compose profile; the application then keeps logging to the console.
- **Production:** the same OTLP settings point at a managed backend or a self-hosted collector with Prometheus, Tempo, Loki and Grafana. That is a planned recipe, not shipped. Retention, access control, alerting and sizing are the deployer's responsibility. The local container is never a production component.

## Requirements

- Logs and traces must not contain secrets, tokens or other sensitive data ([API conventions](../../architecture/api/conventions.md)).
- Health endpoints are never public: the management port is not exposed by the reverse proxy ([ADR 0047](../../decisions/0047-health-exposure-and-container-images.md)).
- OTLP log export is not wired. Boot 4.1.1's exporter needs OpenTelemetry's Logback appender (`opentelemetry-logback-appender-1.0`, an alpha-versioned artifact), which `spring-boot-starter-opentelemetry` does not include ([open questions](../../decisions/open-questions.md)).

## Resolved deviation

`spring-boot-admin-starter-server` was unintended and was removed from `pom.xml` in milestone 0. It is not part of this baseline.

## Related

[Milestone 1](../../product/roadmap/milestone-1-walking-skeleton.md).
