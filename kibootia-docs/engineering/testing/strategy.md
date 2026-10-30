# Testing Strategy

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

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
| Frontend | Components and server code, including the BFF sign-in module | Vitest with Testing Library ([ADR 0038](../../decisions/0038-frontend-stack.md)); not yet present |
| End to end | Sign-in, administration, avatar, the example feature, allowed and denied cases | Playwright ([ADR 0038](../../decisions/0038-frontend-stack.md)); runs nightly, before release tags and on pull requests labelled `e2e` |
| Module removal | The application builds and starts with each optional module disabled or removed | A CI job ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)) |
| Tenant isolation, soft delete | Cross-tenant access is impossible; deleted rows are filtered | Testcontainers MySQL tests ([ADR 0028](../../decisions/0028-tenant-ready-schema.md), [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md)) |
| Storage contract | The storage port against the S3 API of the local server | Integration tests ([ADR 0029](../../decisions/0029-s3-object-storage-contract.md)) |

Test starters listed as present are in `pom.xml`. Their presence says nothing about tests existing.

## Observed tests

`BackendApplicationTests.contextLoads` starts the Spring context. Whether it passes was not checked. It would need a reachable database and the security starters' defaults; this is an inference, not a measurement.

## Database for tests

Real MySQL through Testcontainers, not an in-memory substitute, so MySQL-specific behavior and Flyway scripts are exercised. This requires Docker wherever tests run, including CI.

## Open points

Test naming and layout conventions and the test data approach are undecided ([open questions](../../decisions/open-questions.md)).
