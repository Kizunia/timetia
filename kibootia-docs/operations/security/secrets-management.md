# Secrets Management

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. Current repository state does not meet these rules; see Known limitations.

Decisions: [ADR 0015](../../decisions/0015-vault-for-production-secrets.md) (accepted), [ADR 0016](../../decisions/0016-platform-injected-secrets.md) (draft).

## Rules (owner decisions)

- Secrets are never committed to version control.
- Local development reads secrets from environment variables (a root `.env` that is not tracked).
- HashiCorp Vault is the production secret store.
- Production configuration never silently falls back to development credentials.
- Documentation names variables and never records values.

## Delivery model (provisional)

The deployment platform or a Vault agent delivers secrets to the application as environment variables or files. The application has no Vault client, so configuration is the same in shape locally and in production. Spring Cloud Vault was considered and not chosen. Detail is open ([open questions](../../decisions/open-questions.md)).

## Known limitations (as of 2026-10-27)

These are observations from the repository. Their correction is a separate owner task.

1. The root `.env` file is tracked by git, and no `.gitignore` at the repository root excludes it. It was added in the first commit, so it is also in history.
2. `backend/src/main/resources/application.properties` contains a hardcoded datasource password.

Consequences: removing the files from tracking does not remove them from history, so credentials that were ever committed should be treated as exposed and rotated. This document does not reproduce any value.

When the cleanup is done, replace this section with a one-line dated note and update [implementation status](../../product/roadmap/implementation-status.md).

## Security considerations

Compose passwords are for local development only. They must never be reused for any shared or production environment.

## Related

[Application configuration](../configuration/application-configuration.md), [milestone 2](../../product/roadmap/milestone-2-configuration-and-secrets.md).
