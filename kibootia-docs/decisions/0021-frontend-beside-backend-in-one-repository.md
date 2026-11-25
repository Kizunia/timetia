# 0021 Frontend beside backend in one repository

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. There is no `frontend/` directory.

## Context

The repository location of the Next.js app affects layout, CI and setup docs. The owner chose the recommended option, first provisionally and then, on 2026-11-08, as accepted. The Next.js app is a member of the root pnpm workspace ([ADR 0040](0040-local-development-topology-and-setup.md)).

## Decision

The Next.js app lives in `frontend/` next to `backend/` in the same repository.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Separate repository | Docs, Compose and end-to-end flow would span two repositories |
| Undecided until milestone 6 | Leaves layout and CI docs incomplete |

## Consequences

One clone, one Compose environment, shared docs and CI. Backend and frontend release together.

## Status

accepted. Confirmed by the owner on 2026-11-08; resolves OQ-05.

## Related

[Milestone 1](../product/roadmap/milestone-1-walking-skeleton.md), [ADR 0038](0038-frontend-stack.md).
