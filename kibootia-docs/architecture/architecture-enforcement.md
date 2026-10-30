# Architecture Enforcement

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. There is no ArchUnit dependency or architecture test in the repository.

Decision: [ADR 0013](../decisions/0013-archunit-architecture-enforcement.md).

## Purpose

Record the architecture rules that are meant to be enforced by automated tests, so they do not live only in these documents.

## Candidate rules

These come from the project definition and are candidates, not implemented rules. Exact package selectors depend on the undecided [package structure](../engineering/conventions/package-structure.md).

| # | Rule |
|---|---|
| 1 | Application logic must not depend on concrete infrastructure implementations |
| 2 | A feature must not access another feature's private persistence implementation |
| 3 | There are no circular dependencies between features |
| 4 | Designated domain packages must not depend on infrastructure frameworks |
| 5 | Optional capabilities must not introduce unnecessary dependencies; core code does not depend on an optional or default-on module ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)) |
| 6 | Persistence entities are not used in API types (derived from [ADR 0004](../decisions/0004-separate-api-application-and-persistence-models.md); proposed addition) |

## Beyond ArchUnit

The module-removal check is a CI job, not an ArchUnit rule: the application must build and start with each optional module disabled or removed ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)).

## Limits

Rules about features can only be demonstrated with two or more modules. The example feature and the foundation modules supply them ([milestone 5](../product/roadmap/milestone-5-example-feature.md)); until they exist, tests for rules 2 and 3 cannot be meaningful.

## Verification

A rule counts as enforced only when its ArchUnit test exists, runs in CI and passes ([verification standard](../engineering/testing/verification-standard.md)). How exceptions to a rule are handled is not decided.
