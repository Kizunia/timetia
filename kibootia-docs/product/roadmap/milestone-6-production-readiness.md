# Milestone 6: Production Readiness and First Release

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No Dockerfile or deployment configuration exists.

## Scope (owner decision, [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md), [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md))

The reference production deployment, the production guards, the first recipes and the first release.

## Completion criteria (proposed, not yet approved)

- Container images build and are scanned, for the backend and the Next.js server.
- The single-server reference deployment works: reverse proxy with TLS, a separate migration step, secrets from Docker secrets or files, health checks, backup guidance and rollback guidance ([container deployment](../../operations/deployment/container-deployment.md)).
- Production startup fails when development defaults are detected (demo realm, demo passwords, development client secrets, seed data).
- The threat model is complete for every shipped capability.
- The first recipes exist and are marked documented or verified: policy engine (OPA), Vault Agent, RabbitMQ, self-hosted S3 (SeaweedFS), malware scanning, a production telemetry backend, and a server-side session store.
- The production SMTP provider question is settled ([open questions](../../decisions/open-questions.md)).
- Nightly end-to-end tests pass, a release tag exists, and the changelog carries upgrade and security notes ([ADR 0023](../../decisions/0023-template-fork-adoption-model.md)).
- All of the above pass in CI.
