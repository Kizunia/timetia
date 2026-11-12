# Handover checklist

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

What "complete" means for Timetia's authentication and authorization, and what is handed to the other developers. **No item is done yet.** Tick an item only with evidence, such as a named CI run or a reviewed document.

## Complete means

### Behavior

- [ ] A seeded user of each role signs in with Keycloak and sees what the [role and scope model](role-and-scope-model.md) allows and nothing else.
- [ ] Every documented rule in the model has an allowed and a denied test, and they pass in a named CI run.
- [ ] Revocation works: a removed role stops working on the next request.
- [ ] The unknown-registrant and disabled-account cases behave as decided ([OQ-A8](open-questions.md)).

### Security

- [ ] Hardening items R1, R2, R3 and R7 are fixed and tested. R4, R5 and R6 are fixed or accepted in writing. Other backlog items are fixed or recorded as accepted.
- [ ] No use case is unprotected. The architecture tests pass with no exceptions for Timetia modules.
- [ ] Refusals and administration changes are audited with ids only.
- [ ] No secrets or personal data in logs, audit entries or documents.

### Operations

- [ ] Users and assignments can be imported, repeatedly and safely, by someone following the runbook. *Phase 3: implemented and tested locally; the [runbook](../../operations/provisioning.md) has not been followed by anyone else; rows for HOD, CR, BR and Student are refused until a scope directory exists ([OQ-A15](open-questions.md)).*
- [ ] Super Admin can change an assignment in the browser. *Phase 3: the screen exists and is unit-tested; no browser run, and a role that needs a place fails closed for the same reason.*
- [ ] A fresh environment reaches a working Super Admin without manual database work. *Phase 3: implemented and tested with a stand-in identity provider; not run against Keycloak.*
- [ ] The identity names are Timetia's, and the setup steps use no `kibootia` name except where deliberately kept.

### Delivery

- [ ] The backend, frontend, drift, module and end-to-end checks pass, with the end-to-end run named in the documents.
- [ ] The handover review passed: a teammate added a protected endpoint using only the documents.

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
