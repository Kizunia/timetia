# Implementation plan

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

The ordered work to finish Timetia's authentication and authorization. The plan builds on [current state](current-state.md), delivers the [target architecture](target-architecture.md) and fixes the items in the [hardening backlog](hardening-backlog.md).

## Why this order

The inherited foundation works and is tested in Kibootia. The risk is not "does authentication work". The risks are: the authorization model does not fit Timetia's rules, some rules are undefined, and other developers will code against whatever interface exists first. So:

1. **Settle the undefined rules and the design first**, because the scoped model depends on them.
2. **Harden the inherited code in parallel**, because those fixes do not depend on the design and some are security defects.
3. **Publish the integration contract as soon as the design is fixed**, so teammates build against interfaces, not against code that is still moving.
4. **Build provisioning only after the scoped model**, because imports must create scoped assignments.

## Who does what

| Marker | Meaning |
| --- | --- |
| **Security** | The owner of authentication and authorization |
| **Module** | The teammate who owns the named module or data |
| **Both** | Needs agreement before either side builds |

Each work package ends with a check that can be shown to someone else.

## Status on 2026-11-12

Phases 0, 1 and 2 are implemented. Phase 3 is implemented and, since the local end-to-end run described in [testing and verification](testing-and-verification.md), **shown working against a real Keycloak, MySQL and mail catcher for everything that does not need a scope directory**; what needs one is blocked on its owner (see the gates below). Phase 4 is **partly done**: the worked example, the test fixtures and the per-role end-to-end tests for the roles that can be given are in; the teammate review and the roles that need places are not. "Implemented" means the change and its tests are in the repository; how far each was run is in [testing and verification](testing-and-verification.md). **Nothing is called verified without a named CI run.** There is one for the code: [CI run 43249358335](https://github.com/ishanKulkarni13/Kibootia/actions/runs/43249358335) on PR #8 at commit `eee70f7`, all nine jobs green, including the end-to-end suite (39 passed, 3 skipped). It does not cover the bootstrap run, the runbook, the roles that need places or the teammate review.

| Work package | Status |
| --- | --- |
| W0.1 to W0.4 | Done. The decisions are in [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md) and [ADR 0005](../../decisions/0005-identity-hardening-decisions.md); defaults and open questions are in [open questions](open-questions.md). OQ-A10 stays open for Phase 3. |
| W1.1, W1.2 | Implemented, with tests. |
| W1.3 | Implemented, with tests. First-sign-in, default-role, avatar and audit-view events are not audited yet. |
| W1.4 | Implemented, with tests. |
| W1.5 | Pinned by a test; documented. |
| W1.6 | Configuration changed. The rewritten end-to-end test has not been run. |
| W1.7 | Implemented, with tests: token type, not-before, clock skew, unsigned and shared-secret tokens, a 403 for `enable`. The test for health on a merged port is replaced by the production guard. |
| W1.8 | Done for identity-facing names. The test realm used by `KeycloakAdminAdapterIT` keeps its own names. |
| W2.1 | Done: [ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md) and the [integration contract](integration-contract.md). Teammates have not yet been told. |
| W2.2 | Done: the catalogue, six role templates and the policy are in the `academic` module. The seeded roles are checked for scope type and a sample of permissions by `AcademicAuthorizationIT`, and every rule has tests in `AcademicAccessPolicyTest`. |
| W2.3 | Done: migration `V202611121000__access_scoped_assignments.sql`, `ScopedAssignmentService`, `GET` and `PUT /api/v1/users/{id}/scoped-roles`, validation, audit. |
| W2.4 | Done: policy-decided permissions, `ScopedResourcePolicy`, `Authorization.reach`. |
| W2.5 | **Dropped from this phase.** The facts carry user ids, so no person directory is needed ([ADR 0004](../../decisions/0004-scoped-assignments-and-policy-decided-permissions.md), point 7). |
| W2.6 | Security's half is done: the facts interfaces and the rules. Nothing implements the facts yet, because the ticket and timetable modules do not exist. |
| W2.7 | Security's half is done: the `ScopeDirectory` port, the fail-closed behavior (**including a directory that throws, which is now refused as `scope_unvalidated` and not a 500**), and `ScopeDirectoryContract`, a test the owner extends to prove their provider. **Nothing provides department, division and batch data, so a role that needs a place cannot be assigned in the running application** until its owner implements the port. The owner is a teammate who is not named in the repository. Tests use a fake that is held to the same contract. |
| W2.8 | Done: each assignment and removal writes one audit entry with the role, the scope type and the place. |
| W3.1 | Implemented, with tests: a CSV import (`POST /api/v1/user-imports`, dry run first), add only, Super Admin only, places named by code, bounded, audited. **Rows that name a place are refused until a scope directory exists**; Super HOD and Faculty import today. Decisions: [ADR 0006](../../decisions/0006-provisioning-and-bootstrap.md). |
| W3.2 | Implemented, with unit tests and, since 2026-11-12, **end-to-end tests run in a browser against the local stack**: check then import a file, a repeat, the refusal when adding HOD in a department on a person's page, and a non-administrator who is refused. Their accessibility checks found real failures (contrast, a table the keyboard could not scroll, a page with no heading), which were fixed. Adding or removing a role that needs a place fails closed for the same reason as W3.1. |
| W3.3 | Implemented, with tests, **and run on 2026-11-12 on a fresh environment against a real Keycloak**: the application invited the configured address at startup, a restart before acceptance sent the link again, the person accepted and became Super Admin at first sign-in (audited once), a restart afterwards sent nothing, and the seeded demo administrator stayed an ordinary user. |
| W3.4 | Written: [provisioning runbook](../../operations/provisioning.md), then **followed by its author** against the local stack (first Super Admin, check, import, repeat, the `account_without_profile` recovery) and corrected. **Not yet followed by someone else**, which is the condition for done. Production steps have not been run: there is no production environment. |
| W4.1 | Done as a test: `AuthorizationWorkedExampleIT`, a ticket service written as another module would write it (record check with 404 and 403, a list filtered from `reach`, the module's own state rule), with a test that its list agrees with the single-record decision for every person and ticket. Quoted in the [integration contract](integration-contract.md). It is not an HTTP endpoint and not a production module. |
| W4.2 | Done: `AcademicApiTest`, so "HOD of department A" is one line. |
| W4.3 | **Partly done.** End-to-end tests with accessibility checks exist for Super Admin, Super HOD and Faculty (`provisioning.spec`, `bootstrap.spec`, `identity.spec`). HOD, CR, BR and Student are blocked on the scope directory. Ran in CI: 39 passed (the bootstrap spec needs its own environment and was run by hand). |
| W4.4 | **Not done.** It needs a teammate. A task brief for it is in the [handover checklist](handover-checklist.md). |
| W4.5 | **Partly done: 3 of 7.** The three that needed only a pointer are done. The four that need a product decision (shared courses, student ticket visibility, the wording of Super Admin's powers, STRUCTURE.md) are open; see [open questions](open-questions.md). The done-when ("the list is empty") is not met. |

**Gates.** The gates of Phases 1 and 2 below ask for a named CI run and an end-to-end run. **Neither exists**, so the gates are **not met**; the work is implemented and passes the local runs in [testing and verification](testing-and-verification.md). Two further things stand between Phase 2 and a working Timetia: a module that provides departments, divisions and batches (so scoped roles can be given), and teammates implementing the facts for their records.

**The Phase 3 gate is not met, and one part of it is blocked.** It asks for "a seeded user of each role signs in and sees the right things in a local environment, shown in an end-to-end test". What is now shown, and what is not:

| Part of the gate | State |
| --- | --- |
| An end-to-end test runs against the local stack | **Met**: 39 passed on a fresh local stack and again in [CI run 43249358335](https://github.com/ishanKulkarni13/Kibootia/actions/runs/43249358335). |
| Super Admin, Super HOD and Faculty each sign in | **Met locally.** They are imported, invited, accept, sign in, see no administration and are refused by the API. |
| They "see the right things" | **Not demonstrable through the application.** No endpoint or screen yet uses the permissions of these roles, and a person cannot see their own roles in a place. The permissions are proven in integration tests with the real decision point (`AcademicAuthorizationIT`, the worked example), not through HTTP. |
| HOD, CR, BR and Student sign in with the right scope | **Blocked.** Nothing implements `ScopeDirectory` ([OQ-A15](open-questions.md)); the application refuses these roles, which is the correct behavior and is itself tested end to end. |
| The runbook was followed by someone else | **Not met.** Followed by its author only. |

So the gate stays open until the scope directory exists and the HOD, CR, BR and Student paths are run, and a teammate has followed the runbook. Nothing was faked to make it pass.


## Phase 0: Decide and document

Goal: the open decisions are answered and written down, so Phase 2 does not stall.

| ID | Work | Who | Done when |
| --- | --- | --- | --- |
| W0.1 | This document set is reviewed and the three ADRs are confirmed | Security | Owner has reviewed |
| W0.2 | Answer the **blocking** open questions OQ-A1 to OQ-A5 and OQ-A14 | Security, with the product owner | Each has a recorded answer |
| W0.3 | Answer OQ-A8 (self-registration), OQ-A9 (Administrator versus Super Admin) and OQ-A10 (student accounts) | Security | Recorded; they gate W1.6, W3.1 and W3.3 |
| W0.4 | Write ADR PLAT-0004 for the scope storage choice and any rule answers that change a design | Security | ADR accepted and listed in the index |

Phase 0 runs **alongside Phase 1**.

## Phase 1: Harden and configure the inherited foundation

Goal: the foundation is safe and carries Timetia's names before anything is built on it. Each item is a small change. Where it is a defect in Kibootia's code it is kept self-contained so it can be offered back.

| ID | Work | Backlog | Who | Done when |
| --- | --- | --- | --- | --- |
| W1.1 | A role can only be given by someone who holds every permission in it, in role assignment and in invitation. Administrator role keeps its own rule. | R1 | Security | Tests: the escalation attempt is refused for another user and for oneself; an administrator can still give any role |
| W1.2 | Add a protection so a holder of the disable or delete permission cannot act on an administrator unless they are one. Define the same for enable and erase | R2 | Security | Tests per action; the last-administrator protection still holds |
| W1.3 | Record refused requests, disabled-account refusals and rate-limit refusals as audit or security events, with ids only | R7 | Security | A refused request produces one event with no personal data; tests prove it |
| W1.4 | Fail startup when development defaults are present in a production profile | R3 | Security | A production profile with the local issuer address does not start; test |
| W1.5 | Decide and document how `update-any` relates to `view-any` for notes, then make code and test agree | R5 | Security | Decision recorded; test added |
| W1.6 | Apply the self-registration decision to the Timetia Keycloak configuration | R4 | Security | Behavior matches [OQ-A8](open-questions.md); an e2e test shows an unknown registrant gets no module access |
| W1.7 | Add the missing security tests: token edge cases (not-before, wrong algorithm, wrong token type), a 403 for `enable`, the management port when merged with the API port | backlog tests list | Security | Tests exist and run in CI |
| W1.8 | Give identity Timetia names: realm, clients, audience, cookies, CSRF header, environment variables, page title. The Java package root stays as Kibootia documents it ([OQ-A13](open-questions.md)) | Naming | Security | Local sign-in works end to end under the new names; no `kibootia` name is left in the places listed in [current state](current-state.md) except where intentionally kept |

**Gate for Phase 1:** the backend and frontend checks pass in CI, an end-to-end run has been made once with a named CI run, and the backlog items R1 to R5 and R7 are closed or explicitly accepted in writing.

Note: W1.8 touches many files. Do it as one separate change after the others so it can be reviewed and reverted alone.

## Phase 2: Scoped authorization

Goal: Timetia's rules can be enforced. Depends on Phase 0.

| ID | Work | Who | Done when |
| --- | --- | --- | --- |
| W2.1 | Fix the design: choose the scope storage (PLAT-0004), confirm the shape of the policy and of the scope filter against the code, write the migration plan. **Publish the integration contract's Phase 2 interfaces** | Security | ADR accepted; contract updated; teammates told |
| W2.2 | Role templates and the permission catalogue: seed the seven roles with their scope types and the permissions from the [role and scope model](role-and-scope-model.md) | Security, with each module owner for their permissions | Seed matches the documented matrix; a test reads the matrix and checks the seed |
| W2.3 | Scoped assignment: schema migration, service, API, validation (scope required for scoped roles, scope must exist), audit entries | Security | Assign, change and remove work; the old role endpoint cannot make a scoped role global |
| W2.4 | Scope-aware policy and list filter, added without changing existing signatures | Security | Allowed, denied and list tests per role and scope pass |
| W2.5 | ~~Person directory port~~ Dropped: the facts carry user ids | Both | Not needed |
| W2.6 | Ticket access facts port and the Timetia policy for revert, review, reject, view and audit view | Both | Every relation row of the matrix has an allowed and a denied test |
| W2.7 | Scope directory port and a fake; implementation by the owner of department, division and batch data | Both | A department's descendants resolve in a test |
| W2.8 | Audit events for scoped assignment changes | Security | Each change writes one entry with ids only |

**Gate for Phase 2:** every documented role rule has a test, the existing architecture rules still pass without exceptions, and no use case is unprotected.

## Phase 3: Provisioning and administration

Goal: real people can be loaded, signed in and administered. Depends on Phase 2 and on OQ-A10.

| ID | Work | Who | Done when |
| --- | --- | --- | --- |
| W3.1 | Define the import format (users, scoped assignments) and build an idempotent import job. Accounts through the identity provider port, invitations through the existing lifecycle | Security | Importing the same file twice changes nothing; a bad row is reported and does not stop the rest; audited |
| W3.2 | Extend the administration screens so Super Admin sees and sets scope. Use the permission-aware pieces that exist | Security | A Super Admin changes an assignment in the browser; a non-admin sees no access; accessibility check passes |
| W3.3 | Bootstrap: the first Super Admin and the relation to the platform Administrator ([OQ-A9](open-questions.md)) | Security | A fresh environment reaches a working Super Admin without manual database work |
| W3.4 | Provisioning runbook: preparing the file, running an import, fixing mistakes, onboarding a term | Security | Someone else follows it successfully |

**Gate for Phase 3:** a seeded user of each role signs in and sees the right things in a local environment, shown in an end-to-end test.

## Phase 4: Integration and handover

Goal: other developers can use authentication and authorization without the owner. Depends on Phases 2 and 3.

| ID | Work | Who | Done when |
| --- | --- | --- | --- |
| W4.1 | A worked example: one protected endpoint with a record check and a filtered list, as the model for every module | Security | It is in the integration contract and runs in the tests |
| W4.2 | Test fixtures that sign a caller in as a role at a scope | Security | A module test can ask for "HOD of department X" in one line |
| W4.3 | End-to-end tests per role: one allowed path and one denied path, with accessibility checks | Security | They run in CI on a labelled pull request |
| W4.4 | Handover review: a teammate adds a protected endpoint using only the documents | Both | They succeed without help; gaps found become document fixes |
| W4.5 | Repair the stale or contradicting documents listed in [open questions](open-questions.md) | Security | The list is empty |

**Gate for Phase 4:** the [handover checklist](handover-checklist.md) is complete.

## Dependencies

```
Phase 0 (decide) ─────────────┐
                              ├──▶ Phase 2 (scoped authorization) ──▶ Phase 3 (provisioning) ──▶ Phase 4 (handover)
Phase 1 (harden, rename) ─────┘
```

- W2.1 needs W0.2 and W0.4.
- W2.3 to W2.8 need W2.1.
- W2.5 to W2.7 need the module owners; W2.4 can proceed with fakes.
- W1.6 needs W0.3. W3.1 needs W2.3 and OQ-A10. W3.3 needs OQ-A9.
- W1.8 should come after W1.1 to W1.7 so the renames do not disturb them.

## Coordinating with the other developers

- **Now:** share the [integration contract](integration-contract.md) and the "what to do before Phase 2 lands" list. They can start without waiting.
- **End of W2.1:** announce the interfaces they will implement (the three ports) and the working permission names.
- **During W2.5 to W2.7:** agree each port with its owner. Until then fakes stand in.
- **Every change to a name they depend on** goes through the security owner (see the contract).

## Out of scope

Groups, delegation of role assignment, an attribute-based policy engine, live multi-tenancy, permission caching, machine clients, production deployment of Keycloak (a Kibootia milestone, not Timetia's first need), and the ERP single sign-on. See [target architecture](target-architecture.md).

## Completion

The work is complete when the [handover checklist](handover-checklist.md) is complete.
