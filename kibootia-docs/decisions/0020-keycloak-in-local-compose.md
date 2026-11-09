# 0020 Keycloak in local Compose with an imported realm

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed, not verified in CI. `compose.yaml` runs Keycloak 26.8.2 and its PostgreSQL, Mailpit and, in the `observability` profile, Grafana LGTM, besides MySQL and Adminer. The realm is applied by OpenTofu as ADR 0045 decided, not imported. Everything started and became healthy locally on 2026-11-09.

> **Partly superseded by [ADR 0045](0045-keycloak-operations.md)**: the version-controlled realm is applied by OpenTofu instead of a first-start import. Running Keycloak in local Compose with a realm that holds no real secrets still holds.

## Context

A working login flow on a fresh clone requires a repeatable Keycloak setup.

## Decision

The local environment runs Keycloak in Docker Compose with a version-controlled realm export containing a development client, sample users and roles. The export contains no real secrets.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Developer brings own Keycloak | Manual, error-prone, harder to verify end to end |
| Keycloak in Compose, configured by hand | Not reproducible |

## Consequences

A realm file must be maintained and must include whatever the token rules need (for example the audience mapper from [ADR 0008](0008-audience-validation-and-deny-by-default.md)). The local stack becomes heavier.

## Status

accepted. The import mechanism is superseded by [ADR 0045](0045-keycloak-operations.md); the rest stays accepted.

## Related

[Authentication](../architecture/identity/authentication.md), [local setup](../engineering/getting-started/local-setup.md).
