# Context and Boundaries

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned.

## Purpose

Define who owns what: Kibootia, the application built on it, and the external systems around it.

## Ownership

| Concern | Owner | Notes |
|---|---|---|
| Technical conventions: package rules, security baseline, persistence conventions, API conventions, configuration approach, test approach | **Kibootia** | Delivered as code, tests and docs |
| Business domain: concepts, features, data models, workflows, screens | **Consuming application** | Never added to Kibootia's core |
| User identities, credentials, login screens, token issuance | **Keycloak** | Kibootia validates tokens and does not store passwords |
| Production secret storage | **Vault** (and the platform that injects secrets) | [secrets management](../operations/security/secrets-management.md) |
| Schema of each feature's tables | The owning **feature** | See [data ownership](data/transactions-and-data-ownership.md) |
| Relational storage engine | **MySQL** | |

## What Kibootia must not do

- Embed assumptions of any business domain.
- Store user credentials or act as an identity provider.
- Introduce infrastructure (brokers, orchestrators, search, metrics stacks) without a concrete requirement; the exclusions are in [goals and non-goals](../product/goals-and-non-goals.md).

## Guarantees to consuming applications

Not yet specified. What the starter promises its consumers (for example, stability of platform packages and the upgrade path) has not been decided. See [open questions](../decisions/open-questions.md) (consumer adoption model).

## Related

[Layers and boundaries](layers-and-boundaries.md), [overview](overview.md).
