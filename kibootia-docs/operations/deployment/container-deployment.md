# Container Deployment

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No Dockerfile or deployment configuration exists.

Decision: [ADR 0037](../../decisions/0037-single-server-compose-reference-deployment.md).

## Scope (owner decision of 2026-10-28)

Document local development and one **reference production deployment**: a single virtual machine running Docker Compose behind a reverse proxy that obtains TLS certificates automatically (for example Caddy). Kubernetes is not an initial requirement. Staging and other environment definitions are not part of this set, and the hosting platform is left to each consuming application.

A successful local Compose start is not evidence of production readiness. Local convenience (demo realm, demo credentials, exposed ports, seed data, the all-in-one telemetry container, the mail catcher) must not carry into production.

## Model

- The backend and the Next.js server are packaged as container images and configured entirely through environment variables or mounted files, identical in shape to local configuration ([application configuration](../configuration/application-configuration.md)).
- Secrets are Docker secrets or root-only files; Vault is a recommended alternative ([secrets management](../security/secrets-management.md)).
- The database schema is changed only by Flyway migrations, run as a separate one-shot step that must succeed before the application starts ([schema migrations](../../architecture/data/schema-migrations.md)).
- Managed MySQL and managed S3-compatible storage are recommended; running them in Compose is allowed.
- The backend is not exposed to browsers; it sits behind the Next.js server and may be exposed to non-browser bearer-token clients.
- Production startup fails when development defaults are detected ([token validation](../../architecture/identity/token-validation.md)).
- Limiting request rates at the proxy is required, in addition to the in-app limiter.

## Guidance to be written

Health checks and readiness, backups and restore, scaling beyond one instance, rollback, resource sizing. The one-instance reference is not horizontally scalable by itself; the step to several instances is documented, not built.

## Not decided

Image build method, base image, the Java version in the image (follows [ADR 0019](../../decisions/0019-java-21-baseline.md) unless the deferred Java decision changes it), and the health check contract. All in [open questions](../../decisions/open-questions.md).
