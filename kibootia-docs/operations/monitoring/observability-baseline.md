# Observability Baseline

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. No actuator dependency and no logging configuration exist.

## Scope (owner decision)

The initial foundation provides:

1. Basic logging.
2. Request correlation.
3. Health checks.

Advanced metrics, distributed tracing, dashboards and a Prometheus/Grafana stack are deferred until operational need justifies them.

## Requirements to settle during milestone 5

- A correlation ID per request, present in log lines and available to callers.
- Logs must not contain secrets, tokens or other sensitive data ([API conventions](../../architecture/api/conventions.md)).
- A health endpoint, and whether it is public (the [token policy](../../architecture/identity/token-validation.md) provisionally allows health only as a public endpoint).

## Observed deviation

`spring-boot-admin-starter-server` is in `pom.xml`. The owner states it is unintended and flagged for removal; it is not part of this baseline ([open questions](../../decisions/open-questions.md)).

## Related

[Milestone 5](../../product/roadmap/milestone-5-api-and-operational-conventions.md).
