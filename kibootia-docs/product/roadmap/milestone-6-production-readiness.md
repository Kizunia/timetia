# Milestone 6: Production Readiness and First Release

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. Only the first Dockerfiles of milestone 1 exist by then; no deployment configuration exists today.

## Scope

The reference production deployment, the production guards, the first recipes and the first release. Decisions: [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md), [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0045](../../decisions/0045-keycloak-operations.md), [ADR 0047](../../decisions/0047-health-exposure-and-container-images.md).

## Completion criteria (proposed; rewritten on 2026-11-08 after the second decision round, awaiting owner approval)

- Container images build and are scanned for the backend and the Next.js server.
- The single-server reference deployment works: reverse proxy with TLS, Keycloak on `auth.<domain>` with its admin console blocked, a separate migration step, an OpenTofu realm step with the production overlay, secrets from Docker secrets or files, health checks on the management port, backup guidance covering MySQL, PostgreSQL and storage together, and rollback guidance ([container deployment](../../operations/deployment/container-deployment.md)).
- OpenTofu's state backend works on the recommended production S3 service, or the root-only file fallback is documented (check OQ-47).
- Production startup fails when development defaults are detected (demo realm, demo passwords, development client secrets, seed data).
- The threat model is complete for every shipped capability.
- The first recipes exist and are marked documented or verified: policy engine (OPA), Vault Agent, RabbitMQ, self-hosted S3 (SeaweedFS), malware scanning, a production telemetry backend, a server-side session store, and production Keycloak email settings.
- The licence is chosen and recorded (OQ-48), and Keycloak's production SMTP guidance is written (OQ-36).
- Nightly end-to-end tests pass, a release tag exists, and the changelog carries upgrade and security notes ([ADR 0023](../../decisions/0023-template-fork-adoption-model.md)).
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).
