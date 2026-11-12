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
| The import file: encodings, header, quoting, line ends, bad rows, too many rows | `ImportCsvTest` (unit) |
| The import creates and invites people once, gives roles in their places, audits with no address; the same file again changes nothing | `UserImportIT.importAndRepeat` |
| An imported person signs in and holds the permissions of the role in the place | `UserImportIT.importedPersonSignsIn` |
| An existing person only gets missing roles; a dry run is exact and writes nothing and calls nobody | `UserImportIT.existingPerson`, `dryRun` |
| A file cannot give the Administrator role or any platform role; every row is checked as the screen would | `UserImportIT.platformRoles`, `rowRules` |
| A person's rows go together or not at all; repeated lines are skipped; disabled, deleted, ambiguous and orphan accounts are left alone | `UserImportIT.personIsTheUnit`, `duplicateRows`, `disabledAndDeleted`, `accountWithoutProfile` |
| Identity provider failure for one person leaves nothing for them, the rest carry on; a long outage stops the run | `UserImportIT.identityProviderFails`, `identityProviderDown` |
| One real import at a time; only an administrator imports, even with every permission the work seems to need, and the refusal is recorded | `UserImportIT.oneAtATime`, `onlyAdministrators` |
| Unreadable and oversized files are refused with a reason | `UserImportIT.unreadableFiles` |
| With no scope directory, rows that name a place are refused and nobody is invited for them; global roles import | `UserImportWithoutDirectoryIT`, `ScopedAssignmentsWithoutDirectoryIT.codesFailClosed` |
| The rate limit on real imports, and the time budget | `UserImportLimitsIT` |
| Adding or removing one assignment leaves the others alone; places by code, with the same rules as by id; administrators only | `ScopedAssignmentsApiIT.oneAtATime`, `byCode`, `oneAtATimeOnlyAdministrators` |
| The bootstrap invitation: sent once while no administrator exists, resent before the first sign-in, administrator at first sign-in only for the verified address, nothing once one exists, survives an identity provider outage; a missing or invalid setting does nothing | `BootstrapAdministratorIT`, `BootstrapAdministratorInvitationTest` |
| The user page and import screen: controls only for an administrator, the right requests, the words for the refusals; the Import section only for an administrator | `scoped-roles-editor.test.tsx`, `import-screen.test.tsx`, `admin-sections.test.tsx` (Vitest) |
| `update-any` without `view-any` is a 404 | `NotesApiIT` |
| Module boundaries with the new module; architecture rules | `ModularityTest`, `ArchitectureTest` |
| A scope directory that is down refuses the place (409 `access.scope_unvalidated`, never a 500, never an accept), global roles still work, reading falls back to ids; a deleted place cannot be assigned by id or code and its old assignment is shown by id and can be removed | `ScopedAssignmentsApiIT.directoryUnavailable`, `deletedPlace`; `UserImportIT.directoryUnavailableAndDeletedPlace` |
| What every scope directory must do, including tenant separation; the test double is held to it | `ScopeDirectoryContract` (for the data owner to extend), `ScopeDirectoryContractTest` (unit) |
| A list never shows what a record would refuse, nor hides what it would allow, for 11 people and 5 tickets; what each role sees; a changed role applies at once; 404 for what cannot be seen, 403 for what cannot be done, then the module's own state rule | `AuthorizationWorkedExampleIT` (uses `AcademicApiTest`) |
| End to end, against the real Keycloak, database and mail: a file is checked, imported, invited and repeated; a place that cannot be checked is refused and nobody is invited; HOD in a department is refused on the person's page; Super HOD and Faculty accept, sign in, see no administration and are refused by the API | `frontend/tests/e2e/provisioning.spec.ts` |
| End to end: a user cannot change their own email or username in the account console, only their name | `frontend/tests/e2e/identity.spec.ts` |
| End to end, on a fresh environment: the application invites the configured address, the person becomes Super Admin once and is audited once, the seeded demo administrator does not | `frontend/tests/e2e/bootstrap.spec.ts` (needs `E2E_BOOTSTRAP_EMAIL`; see "Verifying the bootstrap") |

The new tests of R1, R2 and the policy were also run against deliberately broken code (the fix removed, the department check disabled, flat checks allowed to satisfy a policy-decided permission) and failed, so they test the rule and not the implementation.

Known gaps:

- No test makes a real Keycloak token pass through the new type check. `ResourceServerIT` uses signed test tokens that carry `typ: Bearer` as Keycloak's access tokens do.
- The end-to-end tests (`frontend/tests/e2e`) **were run locally on 2026-11-12**, after the renames and the removal of registration (see "Phase 3 completion and Phase 4" below): 39 passed. They had never passed before; the first run failed on stale names and on accessibility. **They have not run in CI.**
- The old end-to-end test of the unverified-email flow is gone, because that flow needed self-registration. The rules are covered by backend tests with tokens.
- The disable and delete safeguard for the last administrator is not reachable through the API any more (only an administrator can act on an administrator), so it has no API test; the same safeguard on role removal is tested in `AccessApiIT`.
- Nothing tests the scoped model against a real department, division or batch module, because none exists. In the same way, the import of HOD, CR, BR and Student rows is tested only with a stand-in scope directory, and in the browser only as a refusal.
- **No test shows the permissions of the Timetia roles through HTTP**, because no endpoint uses them yet. They are proven with the real decision point in `AcademicAuthorizationIT` and `AuthorizationWorkedExampleIT`. "A role was assigned" is shown end to end; "the role works at runtime" is shown below the HTTP layer.
- That Keycloak refuses a direct call to its account API that changes an email ([ADR 0007](../../decisions/0007-users-cannot-edit-their-own-email.md)) was not tried; only the account console's fields were observed.
- The `account_without_profile` recovery was reproduced by hand (a script, not a committed test); the import's own tests cover the report, not the recovery.
- The dry run does not contact the identity provider, so no test (and no dry run) shows the case of an address the identity provider has without a profile; the real run reports it as `account_without_profile` (`UserImportIT.accountWithoutProfile`).
- The compensation of a failed new person (the identity provider's account is removed) is the shared code of an invitation and is tested there (`UserAdministrationIT.failedInvitationLeavesNothing`); the import's own tests show the no-leftovers result for a failure of the identity provider, not for a failure inside the transaction.

## Local runs, 2026-11-12

Run by hand on one Windows machine with Docker Desktop, `./mvnw -B verify` in `backend`. **None is a CI run, so nothing here is "verified" in the sense above.** They are "observed".

| When | What | Result |
| --- | --- | --- |
| After Phase 1 | Whole backend suite, 142 unit and 140 integration tests | 282 run, 0 failures, 0 errors |
| After Phase 2 | Whole backend suite, 194 unit tests and the integration tests | 306 run, 305 passed, 1 failed: `OpenApiContractIT` expected the old list of API paths. The list was updated, the nested request type renamed and the committed `backend/openapi.json` regenerated; `OpenApiContractIT`, `NotesOpenApiIT` and `ScopedAssignmentsApiIT` then passed when rerun alone |
| After the last change (`AcademicRolesTest`) | Whole backend suite | The 200 unit tests passed. **The integration suite did not run: Docker Desktop had stopped.** It was not rerun in full on the final commit |
| Frontend | `pnpm install --frozen-lockfile`, `api:generate`, `lint`, `typecheck`, `test` | Installed; the generated client changed by additions only and is committed; lint and typecheck clean; 22 test files and 196 tests passed. `next build` was not run |

### Phase 3, 2026-11-12

Same machine, `./mvnw -B -o verify` in `backend` and `pnpm` in `frontend`. **Observed, not verified: none is a CI run.**

| What | Result |
| --- | --- |
| Whole backend suite, after the last backend source change | 208 unit tests and 335 integration tests, 0 failures, 0 errors, build success. This includes `ModularityTest`, `ArchitectureTest` and `OpenApiContractIT`, which rewrote `backend/openapi.json` (committed) |
| After that run | One assertion was added to `UserImportIT.onlyAdministrators` and that test was run alone (passes). The rest of the suite was not run again |
| Frontend | `api:generate` (the client changed by additions and is committed), `lint` and `typecheck` clean, 25 test files and 215 tests passed, `next build` succeeded |
| The new tests test the rule | Three rules were broken on purpose and the tests failed, then the code was restored: the administrator check of the import (`UserImportIT.onlyAdministrators`), a platform role findable by name (`UserImportIT.platformRoles`), and the grant at the first sign-in (`BootstrapAdministratorIT.becomesAdministrator`). The first mutation was initially **not** caught, because `access` repeats the administrator check for any row it resolves; the test now also sends a file of nothing but mistakes |

**Not run:** the end-to-end tests (no end-to-end test of the new screens was written either: the stack was not started and a test that was never run is not evidence), the accessibility check of the new screens, a real Keycloak (the import and the bootstrap use the stand-in identity provider), the Docker image builds, the vulnerability and secret scans, anything in CI. One unit-test limitation: in the test browser a chosen file loses its content, so the screen test checks that a multipart `file` part is sent, not what is in it.

An earlier whole-suite run during Phase 1 reported 28 failures and 36 errors. They were not defects: source files were edited while that run was in progress, class files disappeared from the output folder (`NoClassDefFoundError`) and test resources went missing. The same code passed on a clean rerun. Do not edit sources while a build runs.

The end-to-end tests, the Docker image builds and the changed Keycloak configuration were run later the same day (next section). Still not run: the vulnerability scan, the secret-scan tool (a manual search of the pending changes for the values in the generated `.env` found none), and anything in CI. The next step for a reviewer is a CI run on the branch, with the `e2e` label.

### Phase 3 completion and Phase 4, 2026-11-12 (evening)

Same machine. **Observed, not verified: none is a CI run.** The Timetia stack was started from this branch (`docker compose up --build`: real Keycloak 26, MySQL, Mailpit, the object store, and both application images), with the stack of the developer's Kibootia project stopped meanwhile and started again afterwards.

| What | Result |
| --- | --- |
| Backend, `./mvnw -B -o clean verify`, on the final code | 217 unit tests and 344 integration tests, 0 failures, 0 errors, BUILD SUCCESS. Includes `ModularityTest`, `ArchitectureTest` and `OpenApiContractIT`; `openapi.json` did not change (no API change) |
| Frontend | `lint` and `typecheck` clean; 25 test files and 215 tests passed; `next build` succeeded |
| End to end, first run | 3 failed, 15 did not run (serial stories stop at a failure), 19 passed. **Every failure was an accessibility violation** found by axe on screens that had never been checked: destructive and muted text under the 4.5:1 contrast minimum, a table that scrolled sideways without keyboard access, a page with no level-one heading. Fixed (`fix(frontend)`), after computing the contrast ratios |
| End to end, final, on a fresh database | **39 passed, 2 skipped** (the bootstrap spec, which needs its own environment), 2.2 minutes, `npx playwright test` |
| Bootstrap on a fresh environment with a non-seeded address | 3 passed. Before it: one invitation at startup, one more after a backend restart, `invited` and `invitation-resent` in the audit log. After it: a restart sent nothing, `granted` once, and the seeded demo administrator was an ordinary user |
| `account_without_profile` recovery, by hand | An account was created in Keycloak that Timetia did not know. The import reported `account_without_profile`; after the account was deleted the same file added the role and sent one invitation, and a third time reported "Already there" |
| Realm change | `tofu apply` planned exactly 1 resource to add, nothing else. The account console's email field went from enabled to disabled; names stayed enabled; the invitation flow still passed |
| The new tests test the rule | (1) The fail-closed change in `ScopedAssignmentService` was reverted: both new tests failed, then it was restored. (2) The theory-only rule of a division grant was removed from the worked example's list query: 2 tests failed, then it was restored |

**What went wrong on the way, so it is not rediscovered:**

- **Do not run `./mvnw` while the Java language server of an editor is building the same folder.** VS Code's Java extension wrote Eclipse-compiler class files into `backend/target/classes` with an older source level, and 25 tests failed with `Unresolved compilation problems … only available with source level 21` (a pattern `switch` in `AvatarService`). `./mvnw clean verify` fixed it and it did not recur. A related problem to the Phase 1 note above (build output changed under a running build).
- **A root `.env` changed the backend tests.** It sets a bootstrap address, so every test context sent a startup invitation and `EventPublicationRegistryIT` failed (2 events, 1 expected). It passed only on a machine with no `.env`, which is why nobody saw it. The test configuration now pins an empty address and no invitation; see `backend/src/test/resources/config/application.properties`.
- **Frontend unit tests failed intermittently, and the cause is not known.** On unchanged code, the suite gave 1 failure, then 65, and single files failed three times in a row with `Invalid Chai property: toHaveTextContent` (the jest-dom matchers were not registered), then passed with no change. It happened while Maven, Docker, Keycloak and Grafana were all running on a machine with about 2 GB free; with the stack stopped the suite passed every time (25 files, 215 tests, three clean runs). One copy of `vitest` is installed, so it is not a duplicate-instance problem. **Run the frontend unit tests with nothing else heavy running; treat a failure of this shape as the machine, and rerun.**
- **This branch's `compose.yaml` fixes container names (`kibootia-*`) and image tags (`kibootia-backend:local`).** Beside a stopped Kibootia stack the names conflict, and building here re-tags the Kibootia images (its existing containers keep their original image). The run used a compose override that renamed only the containers, outside the repository. Renaming them in `compose.yaml` (OQ-A13 kept the names deliberately) would remove the problem.

**Running the end-to-end tests here.** `node scripts/apps.mjs up` then `pnpm --filter kibootia-frontend test:e2e`, with a root `.env` from `pnpm bootstrap`. They need a **fresh database** for a second run: the notes story searches for "Paging" without a stamp, so notes left by an earlier run change its counts. Reset with `docker compose down -v`.

**Verifying the bootstrap.** On an environment with no administrator: set `KIBOOTIA_BOOTSTRAP_ADMIN_EMAIL` in `.env` to an address that is not the seeded demo administrator (for example `ops.admin@timetia.test`), `docker compose down -v`, `docker compose up -d --wait`, then `E2E_BOOTSTRAP_EMAIL=ops.admin@timetia.test npx playwright test tests/e2e/bootstrap.spec.ts` in `frontend`. Put the setting back afterwards: the other specs need `demo-admin` to be the administrator.

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
