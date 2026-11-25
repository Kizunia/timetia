# 0016 Platform-injected secrets (no Vault client in the application)

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Context

[ADR 0015](0015-vault-for-production-secrets.md) fixes Vault but not how the application obtains secrets. The owner chose the recommended option, first labelled provisional and confirmed on 2026-10-28 together with [ADR 0032](0032-secrets-from-environment-and-files.md).

## Decision

The deployment platform or a Vault agent delivers secrets as environment variables or files. The application contains no Vault client.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Spring Cloud Vault in the application | Adds a dependency and couples the app to Vault |
| Decide at milestone 2 | Leaves configuration docs vague |

## Consequences

Same configuration shape locally and in production. Requires a Vault agent or platform integration outside the application; which one is open.

## Status

accepted. Confirmed with [ADR 0032](0032-secrets-from-environment-and-files.md), which makes Vault recommended rather than mandatory.

## Related

[Secrets management](../operations/security/secrets-management.md), [container deployment](../operations/deployment/container-deployment.md).
