# 0032 Secrets from environment and files; Vault recommended

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. Current repository state does not meet the secrets rules; see [secrets management](../operations/security/secrets-management.md).

## Context

[ADR 0015](0015-vault-for-production-secrets.md) names HashiCorp Vault as the production secret store, and draft [ADR 0016](0016-platform-injected-secrets.md) says the application has no Vault client. A mandatory Vault would mean every production deployment, including the single-server reference deployment ([ADR 0037](0037-single-server-compose-reference-deployment.md)), also runs, unseals, backs up and secures a Vault.

## Decision

The application reads secrets **only from environment variables or mounted files**. It has no Vault client. The reference deployment supplies them through Docker secrets or root-only files.

**Vault is recommended, not mandatory.** Teams that need rotation and audit run Vault and deliver secrets through a Vault Agent; that is a recipe. Local secrets are generated per developer, and production startup fails when development defaults are detected (demo realm, demo passwords, development client secrets, seed data).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Vault mandatory, delivered by an agent | Heavy for small teams; makes the reference deployment large |
| Spring Cloud Vault in the application | Couples the application to Vault |

## Consequences

The configuration shape is identical locally and in production. Rotation and audit are the deployer's responsibility unless Vault is adopted.

## Status

accepted. Partly supersedes [ADR 0015](0015-vault-for-production-secrets.md): Vault changes from "the" production store to the recommended store; the rules that secrets are never committed and production never falls back to development credentials still hold, so ADR 0015 stays accepted. Confirms [ADR 0016](0016-platform-injected-secrets.md) (no Vault client), which becomes accepted in the same change. Resolves OQ-04.

## Related

[Secrets management](../operations/security/secrets-management.md), [container deployment](../operations/deployment/container-deployment.md).
