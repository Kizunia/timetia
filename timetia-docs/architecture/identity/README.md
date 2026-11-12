# Identity and access

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

Authentication (who is this person) and authorization (what may they do) for Timetia: what exists, what will be built, the plan to build it and the contract other modules use.

Timetia started as Kibootia's code with no authentication or authorization of its own. Phases 0 to 2 of the [implementation plan](implementation-plan.md) are now implemented: hardening of the inherited foundation, and Timetia's scoped authorization. The [current state](current-state.md) describes the inherited code and what changed. Nothing here is called verified without a named CI run; see [testing and verification](testing-and-verification.md) for what was run locally.

Read in this order:

| File | Summary |
| --- | --- |
| [current-state.md](current-state.md) | What the inherited code does today, classified, with the request path and the component list |
| [target-architecture.md](target-architecture.md) | The intended Timetia design and what is deliberately left out |
| [role-and-scope-model.md](role-and-scope-model.md) | Roles, scopes, a draft permission catalogue and the facts the ticket module must supply |
| [integration-contract.md](integration-contract.md) | What other modules may depend on, what arrives later and what is internal |
| [implementation-plan.md](implementation-plan.md) | The phases and work packages, with owners, dependencies and gates |
| [hardening-backlog.md](hardening-backlog.md) | Defects and gaps found in the inherited code, each with a fix described and a proving test |
| [testing-and-verification.md](testing-and-verification.md) | Definition of done, the role and scope test matrix and how to run the tests |
| [open-questions.md](open-questions.md) | Decisions that still need the owner, with a recommendation each, and document repairs |
| [handover-checklist.md](handover-checklist.md) | What "complete" means and what the handover package contains |

Decisions: [0001 authentication follows Kibootia](../../decisions/0001-authentication-follows-kibootia.md), [0002 scoped roles on the current Kibootia contract](../../decisions/0002-scoped-roles-on-the-current-kibootia-contract.md), [0003 role administration by import and Super Admin](../../decisions/0003-role-administration-by-import-and-super-admin.md).

Evidence for the findings: [authn-authz-audit](../../research/authn-authz-audit.md).

## The design this is built on

Kibootia's committed identity documents describe the inherited design in full. This folder records only what is specific to Timetia and links to Kibootia for the rest.

- [Authentication](../../../kibootia-docs/architecture/identity/authentication.md)
- [Authorization](../../../kibootia-docs/architecture/identity/authorization.md)
- [Token validation](../../../kibootia-docs/architecture/identity/token-validation.md)
- [User lifecycle](../../../kibootia-docs/architecture/identity/user-lifecycle.md)
- [Documentation standards](../../../kibootia-docs/meta/documentation-standards.md)
