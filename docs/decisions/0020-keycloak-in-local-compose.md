# 0020 Keycloak in local Compose with an imported realm

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. `compose.yaml` has only MySQL and Adminer.

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

accepted

## Related

[Authentication](../architecture/identity/authentication.md), [local setup](../engineering/getting-started/local-setup.md).
