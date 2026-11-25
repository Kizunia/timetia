# 0001 Authentication follows Kibootia

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

## Context

Timetia's documents never say how users sign in. The lecture-rescheduling module is standalone in version 1 and "seeded manually" ([ADR LR-0001](../modules/lecture-rescheduling/decisions/0001-standalone-version-1.md)). The original spec assumed single sign-on against the ERP, which is now only a long-term target ([ERP ownership](../modules/lecture-rescheduling/integration/erp-ownership.md)). The data model fixes proposed a local password column (fix F-A2 in [data model fixes](../research/data-model-fixes.md)).

Timetia is a fork of Kibootia. Its code and its identity documents are, at the time of writing, the same as Kibootia's. Kibootia keeps credentials only in Keycloak ([Kibootia ADR 0025](../../kibootia-docs/decisions/0025-identity-and-authorization-data-split.md)).

## Decision

Timetia **follows Kibootia's authentication design unchanged**.

- Keycloak is the only place that stores credentials. The Timetia backend stores no password and no password hash ([Kibootia ADR 0006](../../kibootia-docs/decisions/0006-keycloak-oidc-identity.md)).
- The browser signs in through the Next.js server (backend for frontend). Tokens never reach the browser ([Kibootia ADR 0026](../../kibootia-docs/decisions/0026-nextjs-bff-session.md)).
- Tokens prove identity only. Roles and permissions are stored in Timetia's database and read on every request ([Kibootia ADR 0025](../../kibootia-docs/decisions/0025-identity-and-authorization-data-split.md)).
- The user lifecycle is Kibootia's: invitation, first sign-in, active, disabled, deleted, erased ([Kibootia ADR 0041](../../kibootia-docs/decisions/0041-user-lifecycle-and-account-model.md)).
- Data model fix **F-A2 is not adopted.** `UserAccount.password_hash` and `auth_provider` are not created. The identity record is Kibootia's `user_profile`, keyed by the Keycloak `sub`.
- A college or ERP single sign-on, when it is wanted, is added later as an **identity provider federated into Keycloak**. It is not a second login stack in Timetia.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Federate a college or Google identity provider from the start | Needs provider details and a mapping from its accounts to Timetia users that nobody has decided. Can be added later without changing the backend. |
| Local passwords in Timetia's own user table (fix F-A2) | Contradicts [Kibootia ADR 0025](../../kibootia-docs/decisions/0025-identity-and-authorization-data-split.md), adds a password store to secure, and needs a custom login stack. |

## Consequences

- No authentication code has to be designed for version 1. The work is configuration, naming and hardening (see the [implementation plan](../architecture/identity/implementation-plan.md)).
- The Keycloak realm, clients, audience, cookie and header names still say `kibootia` and must be renamed deliberately.
- Kibootia turns **self-registration on**. Timetia's users are seeded. Whether to turn it off in the Timetia realm is **not decided here**; it is [OQ-A8](../architecture/identity/open-questions.md).
- Fix F-A2 in the [data model fixes](../research/data-model-fixes.md) is now out of date and needs a pointer to this decision (listed in the open questions as a document repair).

## Status

Accepted, 2026-11-11, by the project owner.
