# Milestone 6: Frontend Integration

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned (no frontend exists).

## Scope (owner decision)

Integrate the Next.js frontend. The frontend consumes deliberate backend API contracts and never depends on entity structures or backend internals; the backend stays authoritative. The login flow is deliberately deferred to this milestone.

## Completion criteria (proposed, not yet approved)

- The frontend location is settled ([ADR 0021](../../decisions/0021-frontend-beside-backend-in-one-repository.md), draft; provisional `frontend/` beside `backend/`).
- The login flow is decided, recorded as an ADR, and documented in [authentication](../../architecture/identity/authentication.md).
- A protected page calls a protected backend endpoint end to end, with the allowed and denied outcomes both exercised by an automated check.
- Local setup instructions cover frontend and backend together.
- Router, version and test tooling for Next.js are decided or listed as open.
