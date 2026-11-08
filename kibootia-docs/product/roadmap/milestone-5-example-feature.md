# Milestone 5: Example Feature

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Scope

One small, generic, deletable example feature (working name "Notes") that shows applications how to add their own: CRUD, validation, pagination and filtering, ownership rules and list scopes through the decision point, tenant isolation, a file attachment, an event, its own migrations and tests at every level. It also completes the module-removal test matrix. Decision: [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md).

## Completion criteria (proposed; rewritten on 2026-11-08 after the second decision round, awaiting owner approval)

- The example feature demonstrates each pattern above in backend and frontend, meets the [definition of done](../../engineering/testing/verification-standard.md#definition-of-done-for-a-feature), and its documentation explains how to copy the pattern.
- With several modules in place, the Spring Modulith and ArchUnit rules about modules, cycles and authorization checks are meaningful and pass ([architecture enforcement](../../architecture/architecture-enforcement.md)).
- A removal recipe exists for the example feature and for each optional module. CI builds and starts the application with each of them removed or disabled.
- A Playwright flow covers sign-in, administration, avatar and the example feature, including denied cases and an automated accessibility check.
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).
