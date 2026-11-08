# Secrets Management

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. Current repository state does not meet these rules; see Known limitations.

Decisions: [ADR 0015](../../decisions/0015-vault-for-production-secrets.md) (accepted, partly superseded), [ADR 0016](../../decisions/0016-platform-injected-secrets.md) (accepted), [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md) (accepted).

## Rules (owner decisions)

- Secrets are never committed to version control.
- Local development reads secrets from environment variables (a root `.env` that is not tracked), generated per developer by `pnpm setup` from `.env.example` ([ADR 0040](../../decisions/0040-local-development-topology-and-setup.md)).
- In production the application reads secrets only from environment variables or mounted files. It has no Vault client.
- Production configuration never silently falls back to development credentials, and startup fails if development defaults are detected (demo realm, demo passwords, development client secrets, seed data).
- Documentation names variables and never records values.

## Delivery model

The deployment supplies secrets as environment variables or files. The reference deployment uses Docker secrets or root-only files ([container deployment](../deployment/container-deployment.md)). HashiCorp Vault is the **recommended** store for teams that need rotation and audit, delivered by a Vault Agent; that is a planned recipe in the [technology catalogue](../../technology/README.md), not a requirement. The configuration looks the same locally and in production.

## Secrets the foundation will have

The Next.js client secret, the Keycloak service-account secret used for user administration, MySQL credentials, Keycloak's PostgreSQL credentials, Keycloak's SMTP credentials, the Keycloak administrator credentials used only by the OpenTofu deployment step, object storage credentials and the key that encrypts session cookies. OpenTofu's state also contains secrets in plain text and is stored privately and encrypted ([ADR 0045](../../decisions/0045-keycloak-operations.md)). The backend sends no mail and holds no mail credentials. The list is completed in [application configuration](../configuration/application-configuration.md) as each is introduced.

## Known limitations (as of 2026-10-28)

These are observations from the repository. Their correction is a separate owner task ([milestone 0](../../product/roadmap/milestone-0-hygiene-and-guardrails.md)).

1. The root `.env` file was committed in the first commit and is in git history. Later commits (`2a7e2ac`, `51e7dce`) removed it from tracking and added it to `.gitignore`, so it is no longer tracked.
2. `backend/src/main/resources/application.properties` still contains a hardcoded datasource password.

Consequences: removing a file from tracking does not remove it from history, so credentials that were ever committed should be treated as exposed and rotated. This document does not reproduce any value.

When the cleanup is done, replace this section with a one-line dated note and update [implementation status](../../product/roadmap/implementation-status.md).

## Security considerations

Compose passwords are for local development only. They must never be reused for any shared or production environment. The attack surface is listed in the [threat model](threat-model.md).

## Related

[Application configuration](../configuration/application-configuration.md), [milestone 0](../../product/roadmap/milestone-0-hygiene-and-guardrails.md).
