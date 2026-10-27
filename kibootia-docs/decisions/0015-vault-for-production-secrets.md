# 0015 Vault for production secrets

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

Production secrets need controlled storage. Source: project definition, section 6.4.

## Decision

HashiCorp Vault is the intended production secret store. Local development uses environment variables. Secrets are not committed, and production does not fall back to development credentials. How secrets reach the app is in [ADR 0016](0016-platform-injected-secrets.md).

## Alternatives considered

None recorded.

## Consequences

Vault is an operational dependency in production. Known gaps in the current repository are in [secrets management](../operations/security/secrets-management.md).

## Status

accepted

## Related

[Secrets management](../operations/security/secrets-management.md).
