# Milestone 5: Example Feature

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Scope (owner decision, [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md))

One small, generic, deletable example feature (working name "Notes") that shows applications how to add their own: CRUD, validation, pagination and filtering, ownership rules through the decision point, tenant isolation, a file attachment, an event, its own migrations and tests at every level. It also completes the module-removal test matrix.

## Completion criteria (proposed, not yet approved)

- The example feature demonstrates each pattern above, in backend and frontend, and its documentation explains how to copy the pattern.
- With two modules in place, the ArchUnit rules about modules and cycles are meaningful and pass ([architecture enforcement](../../architecture/architecture-enforcement.md)).
- A removal recipe exists for the example feature and for each optional module. CI builds and starts the application with each of them removed or disabled.
- A Playwright flow covers sign-in, administration, avatar and the example feature, including denied cases.
- All of the above pass in CI.
