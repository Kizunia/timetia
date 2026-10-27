# 0018 CI run as the verification standard

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. There is no CI pipeline.

## Context

Milestones must be verified before the project moves on, and documentation must not call untested behavior tested.

## Decision

Behavior is verified, and a milestone complete, only when its automated tests have run in CI on the change and passed. A pipeline that builds and tests every change is part of the foundation.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Local run recorded in a changelog | Not reproducible by others |
| Decide at milestone 5 | Weakens early milestone sign-off |

## Consequences

A CI pipeline must be built early (the CI platform is open). Until then nothing is verified.

## Status

accepted

## Related

[Verification standard](../engineering/testing/verification-standard.md).
