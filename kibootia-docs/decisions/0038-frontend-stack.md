# 0038 Frontend stack

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. There is no `frontend/` directory.

## Context

The router, tooling and conventions for the Next.js frontend were open (OQ-31). The sign-in design is in [ADR 0026](0026-nextjs-bff-session.md).

## Decision

- Next.js App Router, strict TypeScript, pnpm. Versions follow the [version policy](0035-version-adoption-policy.md).
- Tailwind CSS with shadcn/ui; the components are copied into the repository, which suits the ownership model of [ADR 0023](0023-template-fork-adoption-model.md). Accessibility target: WCAG 2.2 AA.
- A typed API client generated from the backend's OpenAPI description, so the frontend cannot drift from the API.
- Server components for first loads; TanStack Query for client-side data that changes.
- React Hook Form with Zod for forms. Server-side validation is authoritative.
- Permission-aware controls driven by the caller's permissions from `/me`. This is for usability only; the backend enforces every check.
- One feature folder per module, mirroring [ADR 0024](0024-package-modules-with-infrastructure-toggles.md).
- Tests: Vitest with Testing Library, and Playwright end-to-end flows (sign-in, administration, avatar upload, the example feature, allowed and denied cases). End-to-end runs nightly, before release tags, and on pull requests labelled `e2e`.

## Alternatives considered

Not itemized: the owner confirmed the proposed package as a whole.

## Consequences

The OpenAPI description becomes part of the backend's contract and must stay accurate.

## Status

accepted. Resolves OQ-31 except exact versions.

## Related

[API conventions](../architecture/api/conventions.md), [testing strategy](../engineering/testing/strategy.md).
