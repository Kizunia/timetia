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

Phases 0 and 1 are implemented and Phase 2 is in progress. Nothing of Phase 3 or Phase 4 is started. "Implemented" means the change and its tests are in the repository; how far each was run is in [testing and verification](testing-and-verification.md). Nothing is called verified without a named CI run, and no CI run has been made of these changes.

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
| W2.1 to W2.8 | In progress; see [implementation notes](#phase-2-notes) below as they land. |

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
| W2.5 | Person directory port and a fake; implementation by the owner of faculty and student records | Both | A user resolves to a faculty or student record in a test |
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
