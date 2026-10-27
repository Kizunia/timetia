# Testing Strategy

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed (one `contextLoads` test, not run for this document) and otherwise planned.

Testing is part of the architecture (owner direction). Verification rules: [verification standard](verification-standard.md).

## Intended test kinds

| Kind | Covers | Tooling |
|---|---|---|
| Unit | Domain and application logic in isolation | JUnit (via Spring Boot starters) |
| Integration | Wiring of beans and adapters | Spring Boot test support |
| Architecture | Dependency and package rules | ArchUnit ([ADR 0013](../../decisions/0013-archunit-architecture-enforcement.md)); not yet a dependency |
| API | Endpoints, validation, error behavior | Spring MVC test support (`spring-boot-starter-webmvc-test` present) |
| Security | Token validation, authentication and authorization outcomes | Spring Security test support (security and OAuth2 resource server test starters present) |
| Persistence and migration | Entities, repositories, Flyway scripts on real MySQL | Testcontainers MySQL ([ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md)); not yet a dependency; `spring-boot-starter-flyway-test` present |
| End to end | A protected flow from frontend to backend | Tooling undecided ([milestone 6](../../product/roadmap/milestone-6-frontend-integration.md)) |

Test starters listed as present are in `pom.xml`. Their presence says nothing about tests existing.

## Observed tests

`BackendApplicationTests.contextLoads` starts the Spring context. Whether it passes was not checked. It would need a reachable database and the security starters' defaults; this is an inference, not a measurement.

## Database for tests

Real MySQL through Testcontainers, not an in-memory substitute, so MySQL-specific behavior and Flyway scripts are exercised. This requires Docker wherever tests run, including CI.

## Open points

Test naming and layout conventions, test data approach, and frontend test tooling are undecided ([open questions](../../decisions/open-questions.md)).
