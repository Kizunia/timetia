# 0005 Identity hardening decisions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

## Context

The [hardening backlog](../architecture/identity/hardening-backlog.md) lists defects in the inherited foundation and gaps in how Timetia configures it. [ADR 0001](0001-authentication-follows-kibootia.md) keeps Kibootia's authentication design. This decision records the choices made while fixing those items in Phase 1.

## Decision

1. **Self-registration is off** in the Timetia Keycloak realm. People arrive by invitation, and by import in Phase 3. The default `User` role carries no Timetia permission. The self-registration test changes to expect no self-service sign-up. This answers [OQ-A8](../architecture/identity/open-questions.md).
2. **Roles are given only by someone who holds every permission in them**, in role assignment and in invitation, and **only an administrator may act on an administrator account** (disable, enable, delete, restore, erase). R1 and R2.
3. **Refusals are recorded as security events.** An authenticated 403, a disabled or deleted account refusal and a rate-limit refusal each write one audit entry, in their own transaction, with ids and permission names only. The number per person per minute is limited so a flood cannot fill the table. **A 401 is logged and not written to the database**, because an unauthenticated caller could otherwise make the application write without limit. R7.
4. **The token type is checked.** A token that carries a type other than `Bearer` is refused, so an ID or refresh token cannot be used as an access token even if the audience mapper changes. T1.
5. **A production guard** exists. With the setting `kibootia.environment` set to `production` (default `local`), startup fails when the issuer or key address is not https or points at the local machine, when a required secret is blank, or when the management port equals the API port. R3 and R6.
6. **Naming depth.** The identity-facing names change to Timetia's: the realm, the clients, the audience, the audience mapper, the cookie names, the CSRF header, the session key label, the browser page title and brand text, and the references to them in Compose, scripts and tests. The Java package root, the `kibootia.*` property prefix and the environment variable names stay, because they follow that prefix and keeping them lets Kibootia changes merge. This answers [OQ-A13](../architecture/identity/open-questions.md).

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Keep self-registration on and rely on the empty default role | Creates active accounts nobody expects and keeps the registration and bot-protection surface. |
| Write every 401 to the audit table | Unbounded writes by unauthenticated callers. |
| Rename the property prefix and package too | Large, breaks merges with Kibootia, and no user or operator sees them. |

## Consequences

- Existing local environments need the realm applied again and new sign-in cookies. Local data in Keycloak is development data.
- The new setting `kibootia.environment` is documented in the identity documents. It is not a secret.
- Nothing here changes how tokens are validated beyond the type check.

## Status

Accepted, 2026-11-12. Point 1 was chosen by the owner. The other points are the author's, recorded for review.
