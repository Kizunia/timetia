# 0037 Single-server Compose reference deployment

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No Dockerfile or deployment configuration exists.

## Context

The documented production environments were a provisional question (OQ-06). The owner wants Kibootia easy to deploy and does not want Kubernetes assumed.

## Decision

The first production deployment documented is a **single virtual machine running Docker Compose**, with a reverse proxy that obtains TLS certificates automatically (for example Caddy). Managed MySQL and managed S3-compatible storage are recommended but optional. Database migrations run as a separate one-shot step that must succeed before the application starts. Secrets come from Docker secrets or root-only files ([ADR 0032](0032-secrets-from-environment-and-files.md)).

Platform-neutral guidance covers health checks, backups, scaling to several instances and rollback. Kubernetes, Helm and other platforms are future or recipe tier. A successful local Compose start is never evidence of production readiness.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Kubernetes with a Helm chart | Heavy learning and operations cost; contradicts the decision to defer Kubernetes |
| Platform-neutral guidance only | No proven path |

## Consequences

The reference deployment is not horizontally scalable by itself; the move to several instances is documented, not built.

## Status

accepted. Resolves OQ-06 and part of OQ-32.

## Related

[Container deployment](../operations/deployment/container-deployment.md).
