# Milestone 2: Configuration and Secrets

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned; current configuration does not meet the target ([secrets management](../../operations/security/secrets-management.md)).

## Scope (owner decision)

Externalise configuration and establish secrets conventions: environment-driven local configuration, Docker Compose, Vault as the production secret store, typed and validated settings with clear failure on missing required values.

## Completion criteria (proposed, not yet approved)

- No secret value in version control, and none in `application.properties`.
- Required settings are validated at startup; a missing one fails with a clear message.
- Production configuration has no fallback to development credentials.
- [Configuration](../../operations/configuration/application-configuration.md) and [secrets management](../../operations/security/secrets-management.md) are accurate, and the [local setup](../../engineering/getting-started/local-setup.md) works from a clean clone.
- The injection mechanism for production secrets ([ADR 0016](../../decisions/0016-platform-injected-secrets.md), draft) is confirmed or replaced.

## Related

Cleanup of the tracked `.env` and the hardcoded password is a separate owner task; see [open questions](../../decisions/open-questions.md).
