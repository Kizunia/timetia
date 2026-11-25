# Architecture Enforcement

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** observed. Spring Modulith verification (`ModularityTest`) and ArchUnit rules 1, 4 to 11 (`ArchitectureTest`) run on six modules with code, and `ArchitectureRulesFireTest` shows that rules 5, 7, 9, 10 and 11 fail on classes made to break them (local run, 2026-11-11; no CI run for the milestone 5 changes yet). Rule 7 and rules 9 to 11 were verified in CI for milestone 2 (run 42605511477) and rule 5 for `files` for milestone 4 (run 42873216224); rule 5 now covers `notes` as well.

Decisions: [ADR 0013](../decisions/0013-archunit-architecture-enforcement.md), [ADR 0039](../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [ADR 0044](../decisions/0044-authorization-enforcement-conventions.md).

## Purpose

Record the architecture rules that are meant to be enforced by automated tests, so they do not live only in these documents.

## Two tools

- **Spring Modulith** verifies the module level: no cycles between modules, and no module reaches into another module's private sub-packages.
- **ArchUnit** verifies rules inside modules and project-wide conventions.

## Rules

Decided rules. Rules 1 and 4 to 11 are implemented in `ArchitectureTest` (rule 7 and rules 9 to 11 in helper classes next to it); rules 2 and 3 are covered by `ModularityTest`. Package selectors follow [package structure](../engineering/conventions/package-structure.md).

| # | Rule |
|---|---|
| 1 | Application logic must not depend on concrete infrastructure implementations (the `web`, `persistence`, `keycloak` and `s3` packages) |
| 2 | A feature must not access another feature's private persistence implementation (Spring Modulith) |
| 3 | There are no circular dependencies between features (Spring Modulith) |
| 4 | Designated domain packages must not depend on infrastructure frameworks |
| 5 | Optional capabilities must not introduce unnecessary dependencies; core code does not depend on an optional or default-on module ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)). Implemented for every module in `REMOVABLE_MODULES` (`files` since milestone 4, `notes` since milestone 5): nothing outside it depends on it, and other modules use the `platform.files` port ([ADR 0051](../decisions/0051-milestone-4-implementation-decisions.md), [ADR 0052](../decisions/0052-milestone-5-implementation-decisions.md)). A new removable module is added to that list |
| 6 | Persistence entities are not used in API types ([ADR 0004](../decisions/0004-separate-api-application-and-persistence-models.md)) |
| 7 | Every public use-case method carries an authorization check or an explicit `@NoAuthorization("reason")` ([ADR 0044](../decisions/0044-authorization-enforcement-conventions.md)) |
| 8 | The `platform` module contains no business logic and depends on no other module. Proposed (an inference from the module map, not an owner decision); implemented for the dependency half (`platform` is declared with no allowed dependencies and ArchUnit checks it); "no business logic" is not machine-checkable |
| 9 | Every `@Scheduled` method carries `@SchedulerLock`, because an unlocked job runs on every instance at once ([ADR 0031](../decisions/0031-transactional-outbox-and-locked-jobs.md), [ADR 0049](../decisions/0049-milestone-2-implementation-decisions.md)) |
| 10 | No method uses `@ApplicationModuleListener`: it opens its transaction before the tenant is bound. Durable listeners use `@DurableEventListener` |
| 11 | A `@DurableEventListener` method takes a `DomainEvent` as its first parameter, because the tenant and trace are restored from the event's context |

## Beyond ArchUnit

The module-removal check is a CI job, not an ArchUnit rule: the application must build and start with each default-on module disabled or removed. The job `module-removal` runs it ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0052](../decisions/0052-milestone-5-implementation-decisions.md), [recipes](../recipes/README.md)). Rule 5 is what makes removal possible; the job is what proves it.

## Limits

Rules about features can only be demonstrated with two or more modules; the six modules of the foundation and the example feature supply them since [milestone 5](../product/roadmap/milestone-5-example-feature.md). A rule that says "no class may..." holds just as well for a typo or for a module that was removed, so each rule that allows an empty match is also pointed at classes made to break it and at classes made to satisfy it (`ArchitectureRulesFireTest`, fixtures under `platform.archfixtures` in the test sources). Spring Modulith, which owns rules 2 and 3, analyses only main classes, so a broken module cannot be fed to it that way; `ModularityTest` shows instead that the core modules depend on each other (and `platform` on none), so there are edges for the verification to check. Rule 7 does not see a check made inside a lambda or a helper class.

## Verification

A rule counts as enforced only when its ArchUnit test exists, runs in CI and passes ([verification standard](../engineering/testing/verification-standard.md)). How exceptions to a rule are handled is not decided.
