# 0017 Testcontainers MySQL for persistence tests

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. Testcontainers is not a dependency.

## Context

The definition requires persistence and migration tests. Tests on a different engine can pass where MySQL fails.

## Decision

Persistence and migration tests run against a real MySQL (matching the Compose version) started by Testcontainers.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| The Compose MySQL | Shared mutable state and a manual start |
| H2 in MySQL mode | Dialect and migration differences can hide failures |

## Consequences

Docker is required wherever these tests run, including CI ([ADR 0018](0018-ci-as-verification-standard.md)).

## Status

accepted

## Related

[Testing strategy](../engineering/testing/strategy.md), [schema migrations](../architecture/data/schema-migrations.md).
