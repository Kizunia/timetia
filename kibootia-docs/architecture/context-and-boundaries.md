# Context and Boundaries

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Purpose

Define who owns what: Kibootia, the application built on it, and the external systems around it.

## Ownership

| Concern | Owner | Notes |
|---|---|---|
| Technical conventions: package rules, security baseline, persistence conventions, API conventions, configuration approach, test approach, observability | **Kibootia** | Delivered as code, tests and docs |
| Foundation modules: user and role administration, profiles and avatars, audit log, object storage | **Kibootia** | Default-on modules that applications can remove or replace ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)) |
| Business domain: concepts, features, data models, workflows, screens | **Consuming application** | Never added to Kibootia's foundation modules |
| User identities, credentials, login screens, token issuance, user lifecycle | **Keycloak** | Kibootia drives the lifecycle through the Admin API ([ADR 0025](../decisions/0025-identity-and-authorization-data-split.md)) and does not store passwords |
| Profiles, roles, permissions, grants | **Kibootia's database** | Keyed by the Keycloak user ID |
| Production secret storage | **The deployment** | Secrets arrive as environment variables or files; Vault is recommended ([secrets management](../operations/security/secrets-management.md)) |
| Schema of each module's tables | The owning **module** | See [data ownership](data/transactions-and-data-ownership.md) |
| Relational storage engine | **MySQL** | |
| Object storage server | **An S3-compatible service** | RustFS locally; managed S3 recommended in production ([ADR 0029](../decisions/0029-s3-object-storage-contract.md)) |

## What Kibootia must not do

- Embed assumptions of any business domain.
- Store user credentials or act as an identity provider.
- Introduce infrastructure (brokers, orchestrators, search, a production telemetry stack) without a concrete requirement; the exclusions are in [goals and non-goals](../product/goals-and-non-goals.md).

## Guarantees to consuming applications

Applications adopt Kibootia by copying it and own all of the code ([ADR 0023](../decisions/0023-template-fork-adoption-model.md)). Kibootia promises:

- tagged SemVer releases;
- a changelog that marks security fixes and gives upgrade notes;
- foundation code in stable, predictable locations, so that merging a release stays tractable.

Kibootia does not promise API compatibility between releases, since nothing is published as a library, and it cannot push fixes to applications. Applications upgrade by merging a release tag.

## Related

[Layers and boundaries](layers-and-boundaries.md), [overview](overview.md).
