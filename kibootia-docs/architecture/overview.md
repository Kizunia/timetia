# Architecture Overview

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. Observed today: a Spring Boot backend skeleton and a Compose MySQL ([implementation status](../product/roadmap/implementation-status.md)).

## Purpose

One page showing the intended building blocks and where the details live. It does not restate the details.

## Intended system context

```mermaid
flowchart LR
    user([User]) --> fe[Next.js server<br/>backend-for-frontend]
    fe -->|API calls with access token| be[Spring Boot backend<br/>modular monolith]
    fe -->|OIDC login| kc[Keycloak]
    be -->|validate tokens| kc
    be -->|Admin API, service account| kc
    be --> db[(MySQL)]
    be --> s3[(S3-compatible<br/>object storage)]
    be -->|invitation mail| mail[SMTP<br/>mail catcher locally]
    be -.->|OTLP| otel[Telemetry backend<br/>Grafana LGTM locally]
    fe -.->|OTLP| otel
```

Notes on the diagram:

- The browser talks only to the Next.js server, which holds the tokens ([ADR 0026](../decisions/0026-nextjs-bff-session.md)). The backend has no CORS for browser traffic.
- Keycloak owns identity; Kibootia's database owns roles, permissions and profiles ([ADR 0025](../decisions/0025-identity-and-authorization-data-split.md)).
- Storage is reached through an S3 API contract ([ADR 0029](../decisions/0029-s3-object-storage-contract.md)); telemetry leaves the application only as OTLP ([ADR 0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md)).
- Secrets reach the application as environment variables or files; Vault is a recommended source, not a component of the diagram ([ADR 0032](../decisions/0032-secrets-from-environment-and-files.md)).
- Nothing in this picture except the backend skeleton and MySQL exists in the repository.

## Building blocks

| Block | Role | Details |
|---|---|---|
| Backend | One deployable Spring Boot application, internally divided into modules | [layers and boundaries](layers-and-boundaries.md), [ADR 0002](../decisions/0002-modular-monolith.md), [ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md) |
| Frontend | Next.js server that signs users in and presents the application; consumes deliberate API contracts | [ADR 0026](../decisions/0026-nextjs-bff-session.md), [ADR 0038](../decisions/0038-frontend-stack.md) |
| Keycloak | Identity provider using OpenID Connect | [identity](identity/README.md) |
| MySQL | Relational persistence, schema owned by Flyway migrations | [data](data/README.md) |
| Object storage | Avatars and uploads | [ADR 0029](../decisions/0029-s3-object-storage-contract.md) |
| Telemetry backend | Receives logs, metrics and traces | [observability baseline](../operations/monitoring/observability-baseline.md) |

## Principles that shape the design

Backend authority, explicit boundaries, separate models, secure by default, no dependency without a purpose. Their statements live in [goals and non-goals](../product/goals-and-non-goals.md).

## Related

[Context and boundaries](context-and-boundaries.md), [communication](communication.md), [ADR index](../decisions/INDEX.md).
