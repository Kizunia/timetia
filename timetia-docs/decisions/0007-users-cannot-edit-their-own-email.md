# 0007 Users cannot edit their own email address or username

- **Type:** adr
- **Module:** platform
- **Status:** proposed
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-12

## Context

Timetia treats the email address as the identity of a person. The backend copies the address from the token into the user's profile at every request (`ProfileGate.refresh`), and the provisioning import ([ADR 0006](0006-provisioning-and-bootstrap.md)) gives roles to the profile that holds the address in the file. The first Super Admin is likewise the person who signs in with the configured address.

Keycloak's default user profile lets a signed-in person edit their own `email` in the account console, and this realm has `verify_email = false`, so a changed address is not verified again. Observed on the running stack on 2026-11-12: the demo user's email field in the account console was enabled, and the realm's user profile gave `edit: ["admin","user"]` for `email`.

That allows this: a person with an account changes their address to one that is about to be imported with a role (an HOD, a Super HOD), and the next import gives the role to them. It also weakens the bootstrap rule, which trusts the address in the token. It needs no weakness in Timetia's code, only the identity provider's default.

## Decision

1. **Only an administrator may edit `email` and `username`.** The realm's user profile is written out in `infra/keycloak/base/main.tf` (`keycloak_realm_user_profile`) with `edit = ["admin"]` for those two attributes. The application's own service account and the realm's operators are the administrators. A person keeps the right to edit their first and last name, because the invitation asks for them.
2. **Everything else in the profile is Keycloak's default**, written out because the resource replaces the whole definition.
3. **A typing mistake in an address is corrected by an administrator**, in Keycloak's console, not by the person. The address in Timetia's profile follows the token at the next sign-in.

## Alternatives considered

| Alternative | Why not |
| --- | --- |
| Turn on `verify_email` | A changed address would need to be verified, but it would still be changeable, and the person could verify an address they control, which is the one that matters. It also contradicts [ADR 0005](0005-identity-hardening-decisions.md) point 1's reliance on the invitation for proof of ownership. |
| Match imports on the Keycloak subject, not the address | An import is written before the person has an account; the address is all it has. |
| Leave it and rely on the rarity of the attack | The attacker needs only an account and the address of someone about to be imported, and students' and faculty addresses are public. |

## Consequences

- **Behaviour people can see changes:** the account console shows the email and username greyed out. Someone with a mistyped address cannot fix it themselves.
- **Verified** (`identity.spec.ts`, 2026-11-12): in the account console the email and username are disabled and the first and last name are enabled. The change was applied to a running realm by re-running the configuration container, and the invitation flow (which asks for names) kept working in the end-to-end suite. **Not verified:** that Keycloak refuses a direct call to its account API that sends a changed email. That is Keycloak's documented behaviour for an attribute a user may not edit, and it was not exercised.
- Existing realms need the configuration applied again. A production overlay does not exist yet; when it does, it inherits this from the base.

## Status

Proposed, 2026-11-12. Taken by the author as a security fix within the identity configuration, and **not yet confirmed by the owner**, because it changes what users can do. Reverting it is deleting the two `edit = ["admin"]` lines (or the resource, to return to Keycloak's default).
