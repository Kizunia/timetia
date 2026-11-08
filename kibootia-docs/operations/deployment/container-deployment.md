# Container Deployment

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. No Dockerfile or deployment configuration exists.

Decision: [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md).

## Scope (owner decision of 2026-10-28)

Document local development and one **reference production deployment**: a single virtual machine running Docker Compose behind a reverse proxy that obtains TLS certificates automatically (for example Caddy). Kubernetes is not an initial requirement. Staging and other environment definitions are not part of this set, and the hosting platform is left to each consuming application.

A successful local Compose start is not evidence of production readiness. Local convenience (demo realm, demo credentials, exposed ports, seed data, the all-in-one telemetry container, the mail catcher) must not carry into production.

## Model

- The backend and the Next.js server are packaged as container images from multi-stage, non-root Dockerfiles (backend on `eclipse-temurin:25-jre`, Next.js in standalone mode on Node LTS), built and scanned in CI; Kibootia publishes no images ([ADR 0047](../../decisions/0047-health-exposure-and-container-images.md), [ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md)). They are configured entirely through environment variables or mounted files, identical in shape to local configuration ([application configuration](../configuration/application-configuration.md)).
- Secrets are Docker secrets or root-only files; Vault is a recommended alternative ([secrets management](../security/secrets-management.md)).
- The database schema is changed only by Flyway migrations, run as a separate one-shot step that must succeed before the application starts ([schema migrations](../../architecture/data/schema-migrations.md)).
- Managed MySQL and managed S3-compatible storage are recommended; running them in Compose is allowed.
- Keycloak runs in the same Compose deployment with its own PostgreSQL, its login pages on `auth.<domain>` and its admin console blocked at the proxy (reached through an SSH tunnel). Its realm is applied by an OpenTofu one-shot step with the production overlay; OpenTofu state lives in a private, encrypted S3 bucket with locking, or in a root-only file ([ADR 0045](../../decisions/0045-keycloak-operations.md)).
- Deployment order: database migrations, then realm configuration, each a one-shot step that must succeed, then the applications.
- Health checks use the internal management port; nothing on the API port is public ([ADR 0047](../../decisions/0047-health-exposure-and-container-images.md)).
- Backups cover MySQL, Keycloak's PostgreSQL and object storage, taken consistently so that Keycloak users and Kibootia profiles stay in step after a restore.
- The backend is not exposed to browsers; it sits behind the Next.js server and may be exposed to non-browser bearer-token clients.
- Production startup fails when development defaults are detected ([token validation](../../architecture/identity/token-validation.md)).
- Limiting request rates at the proxy is required, in addition to the in-app limiter.

## Guidance to be written

Backup and restore procedure, scaling beyond one instance, rollback (redeploy the previous image; migrations stay backward compatible through expand-then-contract), resource sizing. The one-instance reference is not horizontally scalable by itself; the step to several instances is documented, not built.

## Not decided

Production SMTP settings for Keycloak and Kibootia's licence ([open questions](../../decisions/open-questions.md)).
