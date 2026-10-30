# Milestone 0: Hygiene and Guardrails

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. The repository has a bare Spring Boot application, no CI and no architecture tests; see [implementation status](implementation-status.md).

## Scope (owner decision, [ADR 0023](../../decisions/0023-template-fork-adoption-model.md), [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md))

Make the repository safe and checkable before features are added: clean up known deviations, externalise configuration, establish the module skeleton with enforced boundaries, and start CI.

**This milestone is blocked until the Java version is decided** (deferred by the owner; see [open questions](../../decisions/open-questions.md)).

## Completion criteria (proposed, not yet approved)

- `spring-boot-admin-starter-server` is removed ([open questions](../../decisions/open-questions.md)), and `adminer` is pinned or removed.
- No secret value is in version control or in `application.properties`; required settings are validated at startup and a missing one fails with a clear message.
- The credentials that were ever committed are rotated (owner task).
- The package layout and module skeleton are decided ([package structure](../../engineering/conventions/package-structure.md)), and ArchUnit tests exist for the rules in [architecture enforcement](../../architecture/architecture-enforcement.md).
- A GitHub Actions pipeline builds and tests the backend, scans for secrets, and runs Renovate ([verification standard](../../engineering/testing/verification-standard.md)).
- The Java baseline in `pom.xml` matches the decision.
- [Application configuration](../../operations/configuration/application-configuration.md) and [local setup](../../engineering/getting-started/local-setup.md) are accurate and work from a clean clone.
- All of the above pass in CI.
