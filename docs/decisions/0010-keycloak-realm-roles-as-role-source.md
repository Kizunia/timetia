# 0010 Keycloak realm roles as role source

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Context

[ADR 0009](0009-role-based-authorization-first.md) needs roles from the token. Keycloak can supply realm roles, client roles, or groups/custom claims. The owner chose realm roles, labelled provisional.

## Decision

Provisional: read roles from the `realm_access.roles` claim and map them to Spring Security authorities through a documented converter.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Client roles (`resource_access.<client>.roles`) | More configuration; client id becomes part of the contract. Better isolation when one realm serves several apps |
| Groups or custom claim | Needs custom Keycloak mappers; harder for consumers to adopt |

## Consequences

Simple to set up. Roles are shared by all clients in the realm. Switching later requires a superseding ADR and a change to the converter and the development realm.

## Status

draft (provisional; listed in [open questions](open-questions.md))

## Related

[Authorization](../architecture/identity/authorization.md), [realm role](../shared/glossary/realm-role.md).
