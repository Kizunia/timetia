# Architecture Enforcement

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** partly observed. Spring Modulith verification (`ModularityTest`) and ArchUnit rules 1, 4, 6 and 8 (`ArchitectureTest`) exist and pass locally (2026-11-08); rules 1, 4 and 6 are vacuous until module code exists. Rules 5 and 7 are not implemented. No CI run yet.

Decisions: [ADR 0013](../decisions/0013-archunit-architecture-enforcement.md), [ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [ADR 0044](../decisions/0044-authorization-enforcement-conventions.md).

## Purpose

Record the architecture rules that are meant to be enforced by automated tests, so they do not live only in these documents.

## Two tools

- **Spring Modulith** verifies the module level: no cycles between modules, and no module reaches into another module's private sub-packages.
- **ArchUnit** verifies rules inside modules and project-wide conventions.

## Rules

Decided rules. Rules 1, 4, 6 and 8 are implemented in `ArchitectureTest`, rules 2 and 3 are covered by `ModularityTest` (meaningful once two modules have code), rules 5 and 7 are not implemented. Package selectors follow [package structure](../engineering/conventions/package-structure.md).

| # | Rule |
|---|---|
| 1 | Application logic must not depend on concrete infrastructure implementations |
| 2 | A feature must not access another feature's private persistence implementation (Spring Modulith) |
| 3 | There are no circular dependencies between features (Spring Modulith) |
| 4 | Designated domain packages must not depend on infrastructure frameworks |
| 5 | Optional capabilities must not introduce unnecessary dependencies; core code does not depend on an optional or default-on module ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)) |
| 6 | Persistence entities are not used in API types ([ADR 0004](../decisions/0004-separate-api-application-and-persistence-models.md)) |
| 7 | Every public use-case method carries an authorization check or an explicit `@NoAuthorization("reason")` ([ADR 0044](../decisions/0044-authorization-enforcement-conventions.md)) |
| 8 | The `platform` module contains no business logic and depends on no other module. Proposed (an inference from the module map, not an owner decision); implemented for the dependency half (`platform` is declared with no allowed dependencies and ArchUnit checks it); "no business logic" is not machine-checkable |

## Beyond ArchUnit

The module-removal check is a CI job, not an ArchUnit rule: the application must build and start with each optional module disabled or removed ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)).

## Limits

Rules about features can only be demonstrated with two or more modules. The example feature and the foundation modules supply them ([milestone 5](../product/roadmap/milestone-5-example-feature.md)); until they exist, tests for rules 2 and 3 cannot be meaningful.

## Verification

A rule counts as enforced only when its ArchUnit test exists, runs in CI and passes ([verification standard](../engineering/testing/verification-standard.md)). How exceptions to a rule are handled is not decided.
