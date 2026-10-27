# 0021 Frontend beside backend in one repository

- **Type:** adr
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. There is no `frontend/` directory.

## Context

The repository location of the Next.js app affects layout, CI and setup docs. The owner chose the recommended option, labelled provisional.

## Decision

Provisional: the Next.js app lives in `frontend/` next to `backend/` in the same repository.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Separate repository | Docs, Compose and end-to-end flow would span two repositories |
| Undecided until milestone 6 | Leaves layout and CI docs incomplete |

## Consequences

One clone, one Compose environment, shared docs and CI. Backend and frontend release together.

## Status

draft (provisional; listed in [open questions](open-questions.md))

## Related

[Milestone 6](../product/roadmap/milestone-6-frontend-integration.md).
