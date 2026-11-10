# Testing Strategy

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed (one `contextLoads` test, not run for this document) and otherwise planned.

Testing is part of the architecture (owner direction). Verification rules: [verification standard](verification-standard.md).

## Intended test kinds

| Kind | Covers | Tooling |
|---|---|---|
| Unit | Domain and application logic in isolation | JUnit (via Spring Boot starters) |
| Integration | Wiring of beans and adapters | Spring Boot test support |
| Architecture | Module boundaries; dependency, package and authorization-check rules | Spring Modulith verification and ArchUnit ([ADR 0013](../../decisions/0013-archunit-architecture-enforcement.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md)); neither is a dependency yet |
| API | Endpoints, validation, error behavior | Spring MVC test support (`spring-boot-starter-webmvc-test` present) |
| Security | Token validation, authentication and authorization outcomes | Spring Security test support (security and OAuth2 resource server test starters present) |
| Persistence and migration | Entities, repositories, Flyway scripts on real MySQL | Testcontainers MySQL ([ADR 0017](../../decisions/0017-testcontainers-mysql-for-persistence-tests.md)); not yet a dependency; `spring-boot-starter-flyway-test` present |
| Frontend | Components and server code, including the BFF sign-in module | Vitest with Testing Library ([ADR 0038](../../decisions/0038-frontend-stack.md)); not yet present |
| End to end | Sign-in, administration, registration and verification, denied cases (avatar and the example feature follow in milestones 4 and 5) | Playwright ([ADR 0038](../../decisions/0038-frontend-stack.md)) against the Compose stack with Keycloak and Mailpit; runs nightly, before release tags and on pull requests labelled `e2e` |
| Keycloak adapter | The Admin API port against a real Keycloak and a mail catcher, with the least-privilege service account | `KeycloakAdminAdapterIT` (Testcontainers) |
| Module removal | The application builds and starts with each optional module disabled or removed | A CI job ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)) |
| Tenant isolation, soft delete | Cross-tenant access is impossible; deleted rows are filtered | Testcontainers MySQL tests ([ADR 0028](../../decisions/0028-tenant-ready-schema.md), [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md)) |
| Storage contract | The storage port against the S3 API of the local server | Integration tests ([ADR 0029](../../decisions/0029-s3-object-storage-contract.md)) |

Test starters listed as present are in `pom.xml`. Their presence says nothing about tests existing.

## Observed tests

`BackendApplicationIT.contextLoads` starts the Spring context. Whether it passes was not checked. It would need a reachable database and the security starters' defaults; this is an inference, not a measurement.

## Database for tests

Real MySQL through Testcontainers, not an in-memory substitute, so MySQL-specific behavior and Flyway scripts are exercised. This requires Docker wherever tests run, including CI.

## Naming, layout and test data (owner decision of 2026-11-08)

- Fast tests without containers are named `*Test` and run by Maven Surefire; tests that start Spring, Testcontainers or other infrastructure are named `*IT` and run by Maven Failsafe.
- Test methods have readable names through `@DisplayName`.
- Tests live in the same module package as the code they test.
- Test data comes from small builder classes per module. There are no shared SQL fixtures; local seed data is for demos, not tests.
- Coverage is reported in CI but no percentage gate fails a build; the required tests are listed in the [definition of done](verification-standard.md#definition-of-done-for-a-feature).
