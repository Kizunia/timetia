# 0013 ArchUnit architecture enforcement

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. There is no ArchUnit dependency.

## Context

Architecture that exists only in documents erodes. Source: project definition, section 5.6.

## Decision

Important package and dependency rules are enforced by ArchUnit tests, in addition to code review.

## Alternatives considered

None recorded.

## Consequences

Package naming must support selectors for the rules ([package structure](../engineering/conventions/package-structure.md)). Rules about feature boundaries need at least two features to be demonstrable.

## Status

accepted

## Related

[Architecture enforcement](../architecture/architecture-enforcement.md).
