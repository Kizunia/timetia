# 0009 Role-based authorization first, extensible later

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

Authentication proves who a caller is; applications also need to decide what they may do. Source: project definition, section 6.3.

## Decision

Authorization is enforced in the backend and starts as role-based. The conventions leave room for contextual policies later. A complete ABAC engine is not an initial requirement. Frontend checks are for usability only.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Full ABAC from the start | Excluded as unnecessary complexity at this stage |
| Frontend-only checks | Not a security control |

## Consequences

Contextual policies (ownership, tenant) will need later ADRs. The source of role data is in [ADR 0010](0010-keycloak-realm-roles-as-role-source.md).

## Status

accepted

## Related

[Authorization](../architecture/identity/authorization.md).
