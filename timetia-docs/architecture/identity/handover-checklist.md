# Handover checklist

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

What "complete" means for Timetia's authentication and authorization, and what is handed to the other developers. **Three items are ticked**, each backed by the named CI run of PR #8 at commit `eee70f7` ([CI run 43249358335](https://github.com/ishanKulkarni13/Kibootia/actions/runs/43249358335): backend, frontend, the module matrix, container build and scan, secret scan, and the end-to-end suite, 39 passed). Nothing is ticked on the strength of a local run alone. The italic note under an item says what is shown and what is left. See [testing and verification](testing-and-verification.md) for the runs.

## Complete means

### Behavior

- [ ] A seeded user of each role signs in with Keycloak and sees what the [role and scope model](role-and-scope-model.md) allows and nothing else. *Shown locally for Super Admin, Super HOD and Faculty (imported, invited, signed in, no administration, API refuses the import). **Not shown** for HOD, CR, BR and Student (no scope directory, [OQ-A15](open-questions.md)), and not shown through HTTP for any Timetia permission, because no endpoint uses them yet.*
- [ ] Every documented rule in the model has an allowed and a denied test, and they pass in a named CI run. *The backend suite passes in the named CI run. **Whether every row of the model has its tests was not re-audited in this pass**; the earlier claim rests on `AcademicAccessPolicyTest`, and the rules marked `?` in the model (OQ-A11, OQ-A12) are denied and not tested as allowed.*
- [x] Revocation works: a removed role stops working on the next request. *Tested at the decision point for roles in places (`AuthorizationWorkedExampleIT.changesApplyAtOnce`, `AcademicAuthorizationIT`) and in the browser for a platform role (`administration.spec`, "taking a role away applies on the user's next request"). Both pass in the named CI run.*
- [x] The unknown-registrant and disabled-account cases behave as decided ([OQ-A8](open-questions.md)). *Registration is off and its address is refused (`registration.spec`); a disabled user is refused on their next request (`administration.spec`). Both pass in the named CI run.*

### Security

- [ ] Hardening items R1, R2, R3 and R7 are fixed and tested. R4, R5 and R6 are fixed or accepted in writing. Other backlog items are fixed or recorded as accepted. *R8 (a user editing their own email) is new and fixed as a proposal pending the owner's confirmation ([ADR 0007](../../decisions/0007-users-cannot-edit-their-own-email.md)). T2 to T12 are recorded, none yet accepted in writing by anyone.*
- [ ] No use case is unprotected. The architecture tests pass with no exceptions for Timetia modules.
- [ ] Refusals and administration changes are audited with ids only.
- [ ] No secrets or personal data in logs, audit entries or documents.

### Operations

- [ ] Users and assignments can be imported, repeatedly and safely, by someone following the runbook. *Check, import, repeat ("Already there") and the `account_without_profile` recovery were run against the real stack, and the import is covered in CI by `provisioning.spec` and `UserImportIT`. The [runbook](../../operations/provisioning.md) was followed by its author only, not by anyone else, so this stays open. Rows for HOD, CR, BR and Student are refused until a scope directory exists ([OQ-A15](open-questions.md)).*
- [ ] Super Admin can change an assignment in the browser. *The screen is unit-tested and, in the browser (CI), shows the refusal when HOD in a department is added. **A successful add or removal in the browser was not exercised**, and a role that needs a place fails closed for the same reason as the import.*
- [ ] A fresh environment reaches a working Super Admin without manual database work. *Run against a real Keycloak on a fresh environment on 2026-11-12 (`bootstrap.spec`: invited at startup, resent after a restart, administrator at first sign-in, audited once, the seeded demo administrator not promoted). **By hand, not in CI**, because it needs an environment with no administrator; see "Verifying the bootstrap" in the testing guide.*
- [ ] The identity names are Timetia's, and the setup steps use no `kibootia` name except where deliberately kept.

### Delivery

- [x] The backend, frontend, drift, module and end-to-end checks pass, with the end-to-end run named in the documents. *[CI run 43249358335](https://github.com/ishanKulkarni13/Kibootia/actions/runs/43249358335) at `eee70f7`: all nine jobs succeeded; the end-to-end job ran 42 tests, 39 passed and 3 skipped (the bootstrap spec, which needs its own fresh environment and was run by hand).*
- [ ] The handover review passed: a teammate added a protected endpoint using only the documents.

## Task brief for the handover review (W4.4)

Give this to a teammate who did not write the identity code, and ask them to use only the documents.

> Add one protected read endpoint and one protected action to a module of yours. The record has a department, a division, a session type and an author. (1) Read the [integration contract](integration-contract.md) and copy the shape of `AuthorizationWorkedExampleIT`. (2) Write the facts for your record. (3) Make the action allowed for the author and for an HOD of the department, and refused for a CR; make the list show an HOD their department, a CR only theory lectures of their division and a BR only lab and tutorial lectures of their batch. (4) Test it with `AcademicApiTest`. (5) Write down every place you had to ask me something or guess; each is a gap in the documents.

It passes when they finish without help. Record who did it, when, and the gaps found, here. **Not done yet.**

## The handover package

All in `timetia-docs`, written to the repository's [documentation standards](../../../kibootia-docs/meta/documentation-standards.md).

| Item | Where |
| --- | --- |
| How authentication and authorization work in Timetia | [current state](current-state.md) and [target architecture](target-architecture.md), updated to say what is built, with the status of each part |
| Roles, scopes and the permission catalogue | [role and scope model](role-and-scope-model.md), final, with the `?` cells answered |
| The integration contract with a worked example | [integration contract](integration-contract.md) |
| Decisions | The accepted ADRs in the [decision index](../../decisions/INDEX.md), including the scope storage decision |
| Provisioning runbook | Under operations, written in W3.4 |
| Test guide and matrix | [testing and verification](testing-and-verification.md) |
| Known limitations and accepted risks | A short list in the [hardening backlog](hardening-backlog.md), with who accepted each and when |
| Open questions still open | [open questions](open-questions.md), each with an owner |

## Known limitations to state

At handover write down, at least: what is out of scope ([target architecture](target-architecture.md)), what depends on Kibootia's later decisions, the end-to-end tests' schedule, the lack of a production Keycloak configuration, and any question left open.

## Who accepts

The owner of authentication and authorization signs off the list. A teammate who integrated confirms the contract works. Record names and dates here when done.
