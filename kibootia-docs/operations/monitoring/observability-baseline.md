# Observability Baseline

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No actuator dependency, logging configuration or telemetry exists.

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
| Health | Actuator liveness and readiness, separate from telemetry |

The application emits OTLP only. Where it goes is deployment configuration, so there is one telemetry path and no duplicate collectors.

## Local and production

- **Locally:** one Grafana LGTM all-in-one development container starts with `docker compose up`. It can be switched off with a property and a Compose profile; the application then keeps logging to the console.
- **Production:** the same OTLP settings point at a managed backend or a self-hosted collector with Prometheus, Tempo, Loki and Grafana. That is a planned recipe, not shipped. Retention, access control, alerting and sizing are the deployer's responsibility. The local container is never a production component.

## Requirements

- Logs and traces must not contain secrets, tokens or other sensitive data ([API conventions](../../architecture/api/conventions.md)).
- A health endpoint exists, and whether it is public is open (the [token policy](../../architecture/identity/token-validation.md) allows health only as a public endpoint).
- OTLP log export in the Spring Boot version in use needs extra wiring that must be verified ([open questions](../../decisions/open-questions.md)).

## Observed deviation

`spring-boot-admin-starter-server` is in `pom.xml`. The owner states it is unintended and flagged for removal; it is not part of this baseline ([open questions](../../decisions/open-questions.md)).

## Related

[Milestone 1](../../product/roadmap/milestone-1-walking-skeleton.md).
