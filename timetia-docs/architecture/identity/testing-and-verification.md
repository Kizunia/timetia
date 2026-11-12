# Testing and verification

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

How authentication and authorization are tested and how "done" and "verified" are decided. The sections "Where each rule is tested" and "Local runs" say what exists and what was run; the commands below are the ones that were used.

## Definition of done for a protected feature

Adapted from Kibootia's [verification standard](../../../kibootia-docs/decisions/0018-ci-as-verification-standard.md) and [Kibootia ADR 0044](../../../kibootia-docs/decisions/0044-authorization-enforcement-conventions.md):

1. For every protected use case: one test that is **allowed** and one that is **denied**.
2. For every list: a test that shows a caller sees only what their scope allows.
3. Backend: Testcontainers for persistence, API tests for error cases, and the module-boundary and architecture tests passing.
4. Migrations in the module's own folder.
5. The generated API description and the frontend client updated, so the drift checks pass.
6. Frontend: permission-aware screens with unit tests, an end-to-end allowed path, one denied path, and an accessibility check.
7. No secrets or personal data in logs or audit entries.
8. The documents updated, including the folder README and the decision index.

## What "verified" means

A statement is called **verified** only when automated tests passed in CI and the run is named in the document. A local run, or a test that exists but was not run, is at most **observed** or **planned**. Documents say which.

## Where each rule is tested

Phases 1 and 2 added these tests. "Unit" needs no Docker; "IT" is an integration test on MySQL.

| Rule | Test |
| --- | --- |
| Every row of the role and scope model, allowed and denied, with another department, division and batch, and the session rule | `AcademicAccessPolicyTest` (unit) |
| The seeded roles match the documented role by permission table | `AcademicRolesTest` (unit); sample checked again in `AcademicAuthorizationIT.seededRoles` (IT) |
| A permission-only check never satisfies a policy-decided permission; the policy is asked only if the caller holds the permission somewhere; a policy cannot grant; unverified, other tenant, no policy, system | `PolicyDecidedPermissionsTest` (unit) |
| `@RequiresPermission` on a policy-decided permission stops startup | `AccessConfigurationTest` (unit) |
| HOD, CR, BR, Student, Faculty and Super HOD get what their assignment gives, in its place only, from the next request, and the administrator holds no ticket permission | `AcademicAuthorizationIT` |
| The reach of a permission for a list: places, everywhere, nothing | `PolicyDecidedPermissionsTest` (unit), `AcademicAuthorizationIT` |
| Who may give a scoped role (administrators only); what a request must say; a place that does not exist; fail closed with no scope directory; audit with ids only | `ScopedAssignmentsApiIT`, `ScopedAssignmentsWithoutDirectoryIT` |
| A scoped role cannot be given as a platform role or on an invitation; platform role changes leave scoped roles alone | `ScopedAssignmentsApiIT` |
| Role rules: scope type required, flat permission refused in a scoped role, only an administrator puts a policy-decided permission in a role, a held role cannot be deleted | `ScopedAssignmentsApiIT` |
| A role is given only by someone who holds its permissions, to others and to oneself; an invitation too (R1) | `AccessApiIT`, `UserAdministrationIT` |
| Only an administrator acts on an administrator (R2) | `UserAdministrationIT` |
| Token: not-before, clock skew of a minute, no signature, shared-secret signature, ID and refresh tokens, no token type | `ResourceServerIT` |
| Refusals are audited once with ids only, throttled per user, a 401 is not written | `SecurityEventsIT`, `SecurityEventsThrottleIT`, `RateLimitIT` |
| The production guard | `RequiredSettingsTest` (unit) |
| `update-any` without `view-any` is a 404 | `NotesApiIT` |
| Module boundaries with the new module; architecture rules | `ModularityTest`, `ArchitectureTest` |

The new tests of R1, R2 and the policy were also run against deliberately broken code (the fix removed, the department check disabled, flat checks allowed to satisfy a policy-decided permission) and failed, so they test the rule and not the implementation.

Known gaps:

- No test makes a real Keycloak token pass through the new type check. `ResourceServerIT` uses signed test tokens that carry `typ: Bearer` as Keycloak's access tokens do.
- The end-to-end tests (`frontend/tests/e2e`) were **not run** after the renames and the removal of registration: they need the whole stack, whose container names and ports the developer's own Kibootia stack was using. The rewritten `registration.spec.ts` in particular is unproven.
- The old end-to-end test of the unverified-email flow is gone, because that flow needed self-registration. The rules are covered by backend tests with tokens.
- The disable and delete safeguard for the last administrator is not reachable through the API any more (only an administrator can act on an administrator), so it has no API test; the same safeguard on role removal is tested in `AccessApiIT`.
- Nothing tests the scoped model against a real department, division or batch module, because none exists.

## Local runs, 2026-11-12

Run by hand on one Windows machine with Docker Desktop, `./mvnw -B verify` in `backend`. **None is a CI run, so nothing here is "verified" in the sense above.** They are "observed".

| When | What | Result |
| --- | --- | --- |
| After Phase 1 | Whole backend suite, 142 unit and 140 integration tests | 282 run, 0 failures, 0 errors |
| After Phase 2 | Whole backend suite, 194 unit tests and the integration tests | 306 run, 305 passed, 1 failed: `OpenApiContractIT` expected the old list of API paths. The list was updated, the nested request type renamed and the committed `backend/openapi.json` regenerated; `OpenApiContractIT`, `NotesOpenApiIT` and `ScopedAssignmentsApiIT` then passed when rerun alone |
| After the last change (`AcademicRolesTest`) | Whole backend suite | The 200 unit tests passed. **The integration suite did not run: Docker Desktop had stopped.** It was not rerun in full on the final commit |
| Frontend | `pnpm install --frozen-lockfile`, `api:generate`, `lint`, `typecheck`, `test` | Installed; the generated client changed by additions only and is committed; lint and typecheck clean; 22 test files and 196 tests passed. `next build` was not run |

An earlier whole-suite run during Phase 1 reported 28 failures and 36 errors. They were not defects: source files were edited while that run was in progress, class files disappeared from the output folder (`NoClassDefFoundError`) and test resources went missing. The same code passed on a clean rerun. Do not edit sources while a build runs.

Not run at all: the end-to-end tests, `tofu validate` of the changed Keycloak configuration, the Docker image builds, the vulnerability scan, the secret scan, and anything in CI. The next step for a reviewer is a CI run on the branch, with the `e2e` label.

## Role and scope test matrix

Derived from the [role and scope model](role-and-scope-model.md). Each row needs an allowed case and a denied case. Rows marked `?` in that model are added only after the question is answered.

| Rule | Allowed | Denied |
| --- | --- | --- |
| A faculty member raises a ticket for a lecture they teach | their own lecture | another faculty's lecture |
| An HOD acts for an absent faculty | a faculty of their department | a faculty of another department |
| A faculty member applies their own change without a review | their own ticket | someone else's ticket |
| A reviewer approves or rejects | a requested reviewer | a person not asked, a Student |
| A CR reviews or rejects | theory lecture of their division | a lab or tutorial lecture; another division |
| A BR reviews or rejects | lab or tutorial lecture of their batch | a theory lecture; another batch |
| An HOD approves a permanent change | their department | another department |
| An HOD reverts a change | any change in their department | a change in another department |
| An author or affected faculty reverts | their own or their affected ticket | an unrelated ticket |
| A CR or BR opens an issue | within their scope and session type | outside it; a Student |
| A Student sees the timetable | their division, batch and shared-course batches | another division |
| A Student sees tickets | results only | the ticket or its log |
| A ticket's audit log is read | author, reviewer, HOD, CR or BR in scope, Super Admin | a Student, an unrelated faculty |
| Super Admin configures rooms, the slot grid and the calendar | allowed | every other role |
| Nobody overrides a conflict | — | every role, including HOD and Super Admin |
| Super HOD acts across departments | any department | — |
| A scoped role is assigned | with a matching scope that exists | without scope, or with a scope of another type |
| A role is assigned | by a person who holds all its permissions | by a person who does not (R1) |
| A user is disabled or deleted | an ordinary user by a holder of the permission | an administrator, by a non-administrator (R2) |
| Revocation | a removed role stops working on the next request | — |

Also test: each endpoint without a token (401), a disabled account (403), an unverified email where it matters, and a record outside the caller's scope (404 where it must not be revealed, 403 where it may be seen).

## Test kinds and where they live

| Kind | Where | Note |
| --- | --- | --- |
| Unit (`*Test`) | `backend/src/test/java/...` | Fast; no containers |
| Integration (`*IT`) | same | Uses a real MySQL in a container; some use Keycloak and Mailpit containers. **Needs Docker.** |
| Architecture | `ArchitectureTest`, `ModularityTest` and the rule tests | Must pass with no exceptions for new modules |
| Frontend unit | `frontend/tests/unit` | Vitest |
| End to end | `frontend/tests/e2e` | Playwright against the whole stack with real Keycloak |

Test helpers a module test can use: `AdminApiTest` (administrator, user, role setup helpers), `GrantedPermissions` (grant a permission for one test), `FakeIdentityProvider`. W4.2 adds a helper that signs in as a role at a scope.

## Commands

Read from the build files, **not run**. Docker must be running for the integration and end-to-end tests.

| What | Command |
| --- | --- |
| Backend unit tests only | `cd backend` then `./mvnw test` |
| Backend everything, as CI does | `cd backend` then `./mvnw -B verify` |
| One integration test | `cd backend` then `./mvnw verify -Dit.test=ResourceServerIT` |
| Startup subset used by the module-removal checks | `cd backend` then `./mvnw -B verify -Dgroups=startup` |
| Frontend lint, types, unit tests | `pnpm --filter kibootia-frontend lint`, `typecheck`, `test` |
| Regenerate the frontend client from the API description | `pnpm --filter kibootia-frontend api:generate` |
| End to end | start the stack with `node scripts/apps.mjs up`, then `pnpm --filter kibootia-frontend test:e2e` |

The frontend package name is renamed in W1.8, so these commands change then.

The integration test `OpenApiContractIT` rewrites `backend/openapi.json`, and CI fails if the file changes, so commit the regenerated file with any API change.

## CI

The CI workflow runs the backend and frontend checks, the generated-file drift checks, the module-removal matrix, the image build with a vulnerability scan, and a secret scan on every pull request. The end-to-end job runs only nightly, on manual start, or on pull requests labelled `e2e`.

**Label every pull request that changes authentication or authorization `e2e`**, so the end-to-end tests actually run before merge. Without the label they can be skipped.

## What the Phase gates require

| Gate | Test evidence |
| --- | --- |
| Phase 1 | Tests for R1, R2, R3, R7 and the token edge cases pass in a named CI run, including one end-to-end run |
| Phase 2 | Every row of the matrix above that applies has an allowed and a denied test |
| Phase 3 | An end-to-end test signs in a seeded user of each role |
| Phase 4 | Per-role end-to-end tests with accessibility checks run in CI on a labelled pull request |

The [implementation plan](implementation-plan.md) says when each gate applies.
