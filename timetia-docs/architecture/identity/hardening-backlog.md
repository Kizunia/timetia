# Hardening backlog

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

Defects and gaps found in the inherited code, each with the fix described in words and the test that should prove it. **None is fixed.** No fix is written as code in these documents.

How sure each finding is: **R1 was checked by reading the source directly** (`RoleAssignmentService` and `RoleService`). The others come from a read-through of the code by a review sweep on 2026-11-11. No test or application was run. Re-check each against the code when its work package starts. Severity is the author's assessment, not a rating from a tool.

Work packages are in the [implementation plan](implementation-plan.md).

## Findings to fix in Phase 1

### R1. A role can be assigned by someone who does not hold its permissions

- **Severity:** high. **Work package:** W1.1. **Checked by reading.** **Status: fixed in code (2026-11-12).** `RoleAssignmentService.requireGrantable` runs in `replaceRolesOfUser` and `assignInitialRoles`; an administrator may give any role. Tests: `AccessApiIT.aRoleCanOnlyBeGivenByAHolderOfItsPermissions`, `UserAdministrationIT.invitingCannotGrantMoreThanTheInviterHolds`. Both were run without the fix and failed (403 expected, 200 or 201 returned), then run with it and passed.
- **Evidence:** `access/application/RoleAssignmentService.replaceRolesOfUser` and `assignInitialRoles` only restrict the Administrator role (`requireAdministrator`). They do not apply the rule that `RoleService.requireGrantable` applies when a role is edited, which is that the editor must hold every permission being added. `identity/application/UserAdministration.invite` calls `assignInitialRoles`, so invitations have the same gap.
- **Impact:** a person with only the permission to assign roles can give any existing custom role to anyone, **including themselves**, and so gain every permission in it.
- **Fix:** when a role is added to a person, the giver must hold every permission in that role. The Administrator role keeps its rule. The check runs in both the role assignment and the invitation path.
- **Proving test:** a user with the assign permission cannot give another user, or themselves, a role holding a permission they lack, and gets 403. An administrator still can. Invitation naming such a role is refused and leaves no account behind.
- **Also decide:** whether a person who can assign roles may also take away a role whose permissions they do not hold. Today they can. Record the decision.

### R2. No rule stops disabling or deleting an administrator

- **Severity:** medium. **Work package:** W1.2. **Status: fixed in code (2026-11-12).** `UserAdministration.requireMayActOn` refuses a non-administrator on disable, enable, delete, restore and erase of an administrator. Test: `UserAdministrationIT.onlyAnAdministratorActsOnAnAdministrator` (failed without the fix, passes with it). The old test that expected the last-administrator conflict from a non-administrator was replaced, because that call is now a 403. The last-administrator conflict on **role removal** is still tested in `AccessApiIT`; on disable and delete it is now unreachable through the API, because the only person who may act on an administrator is another administrator who can sign in.
- **Evidence:** `identity/application/UserAdministration.disable`, `enable`, `delete`, `restore` and `erase` check a permission and protect only the last active administrator and oneself.
- **Impact:** a holder of the disable or delete permission can lock out any administrator but the last.
- **Fix:** only an administrator may act on an administrator account. Define the same rule for enable, restore and erase.
- **Proving test:** a non-administrator holding the permission is refused for each action on an administrator and allowed on an ordinary user.

### R3. The documented production guard does not exist

- **Severity:** medium. **Work package:** W1.4. **Status: fixed in code (2026-11-12).** `RequiredSettingsEnvironmentPostProcessor` checks the settings when `kibootia.environment=production` ([ADR 0005](../../decisions/0005-identity-hardening-decisions.md)). Tests in `RequiredSettingsTest`.
- **Evidence:** Kibootia's [token validation](../../../kibootia-docs/architecture/identity/token-validation.md) says startup fails when development defaults are detected. No such code was found. The defaults in `application.properties` point at the local Keycloak address.
- **Impact:** a production deployment could start with local issuer settings and fail confusingly, or with weak settings and not fail.
- **Fix:** in a production profile, refuse to start if the issuer, key address or audience are the development defaults, or if required secrets are blank (the secret check partly exists in `platform/config/RequiredSettingsEnvironmentPostProcessor`).
- **Proving test:** a production-profile start with development defaults fails with a clear message.

### R4. Anyone can register in Keycloak

- **Severity:** medium. **Work package:** W1.6. **Status: fixed in configuration (2026-11-12).** `registration_allowed = false` in `infra/keycloak/base/main.tf` ([ADR 0005](../../decisions/0005-identity-hardening-decisions.md)). The end-to-end test in `frontend/tests/e2e/registration.spec.ts` was rewritten to expect no sign-up; **it has not been run** (it needs the whole stack). The old end-to-end test of the unverified-email flow is gone, because that flow needed registration; the rules are still covered by backend tests with tokens.
- **Evidence:** `infra/keycloak/base/main.tf` sets `registration_allowed = true`. A registrant is created as an active user with the default `User` role at first sign-in.
- **Impact:** Timetia's users are seeded, so open registration creates accounts nobody expects. Today the default role carries only note permissions. After Timetia permissions exist, the default role must stay empty of them.
- **Fix:** apply the owner's decision. If registration is turned off, invitation and import remain the way in. Either way, keep Timetia permissions out of the default role.
- **Proving test:** end to end, an unknown registrant (or, if registration is off, an unknown sign-in) reaches no module data.

### R5. `update-any` without `view-any` returns 404

- **Severity:** low. **Work package:** W1.5. **Status: pinned (2026-11-12).** Decision: a role needs both `view-any` and `update-any` to change someone else's note. `NotesApiIT.updateAnyNeedsViewAny` pins it. No code change.
- **Evidence:** in `notes/application/NoteService` and `NoteAttachmentService`, an update first calls `visible(id)`, which requires the view permission. A role with only the update-any permission is told the record does not exist.
- **Impact:** confusing behavior; no data leak. It matters as the pattern other modules will copy.
- **Fix:** decide whether update implies view, and make the code and a test agree. Document the answer in the [integration contract](integration-contract.md).

### R7. Refusals and sensitive reads are not recorded

- **Severity:** medium. **Work package:** W1.3. **Status: fixed in code (2026-11-12).** `SecurityEvents` (platform) with `SecurityEventRecorder` (audit) records `security.access.denied`, `security.account.refused` and `security.rate-limit.exceeded`, throttled per user (`kibootia.audit.security-events.max-per-minute`, default 20). A 401 is logged and not written to the database. Tests: `SecurityEventsIT`, `SecurityEventsThrottleIT`, `RateLimitIT`. Not done: first-sign-in provisioning, the default role grant, avatar changes and viewing the audit log are still not audited.
- **Evidence:** `audit/AuditEntry.failedWith` is not called by production code. `platform/web/GlobalExceptionHandler` does not log an access denial. 401 and 403 responses, disabled-account refusals and rate-limit refusals are not audited. Also not audited: first sign-in provisioning, the default role grant, avatar changes and viewing the audit log.
- **Impact:** no trace of attempted misuse, and no answer to "who looked at the audit log".
- **Fix:** record refusals as security events with user ids only (never tokens, emails or names), rate-limited so a flood cannot fill the table. Add the missing administration events.
- **Proving test:** each refusal type produces one event and none contains personal data.

## Other findings

| ID | Finding | Evidence | Plan |
| --- | --- | --- | --- |
| R6 | **Fixed in code and configuration (2026-11-12): the production guard refuses health on the API port, and the container-probe override is documented in `application.properties`.** The health endpoints are public only because they are on a separate port. If that port setting is removed they would be public on the API port. The bind address is fixed to the local machine, which breaks container probes without an override. | `platform/security/SecurityConfiguration`, `management.server.port` and `management.server.address` in `application.properties` | W1.7: add a test for the merged-port case. Decide the override for deployment. |
| T1 | **Fixed (2026-11-12): `UserTokenValidator` requires `typ` = `Bearer`; tests in `ResourceServerIT`.** No check on the token type or the authorized party. ID and refresh tokens are kept out only because Keycloak adds the audience to access tokens only. | `SecurityConfiguration.jwtDecoder` | W1.7 test; decide whether to add a check |
| T2 | Service-account tokens are recognized by claim shape (`client_id`, a `service-account-` username). | `platform/security/UserTokenValidator` | Accept with a test, or replace with a positive check |
| T3 | Email verification is read from the token while a copy sits in the database. They can disagree for up to the token lifetime (5 minutes). | `Authorization.caller()`, `ProfileGate` | Accept; document |
| T4 | Only six administration and upload paths are rate limited. Reads, such as user search and the audit list, are not. | `platform/ratelimit/*` | Decide after the first Timetia endpoints exist |
| T5 | The architecture rule that every use case is checked accepts any call on `Authorization`, including reading the user id. A method that only reads the id passes. | `AuthorizationRules.isChecked` | Tighten or document in the contract |
| T6 | The audit log is append-only by convention (no update or delete in the code). The database does not enforce it. Timetia's data model fixes ask for database enforcement for ticket audit (F-I3). | `db/migration/audit/*`, [data model fixes](../../research/data-model-fixes.md) | Decide with the ticket module owner |
| T7 | No content security policy header on the browser-facing server. | `frontend/next.config.ts` | Decide; record |
| T8 | Local Compose publishes MySQL, Keycloak and Adminer on all interfaces and starts Keycloak in development mode. | `compose.yaml` | Development only; record so it is never used for deployment |
| T9 | No production Keycloak configuration exists, and no OpenTofu validation runs in CI. | `infra/keycloak/overlays/local` only | Out of scope for Timetia's first need; record as a dependency |
| T10 | The end-to-end tests run only nightly, on manual start or on a labelled pull request. Kibootia recorded that they were skipped in its latest run. | `.github/workflows/ci.yml` | Label authorization pull requests `e2e` (see [testing](testing-and-verification.md)) |
| T11 | A committed environment file with database credentials is recorded in the history of the older data model work. | [STRUCTURE.md](../../STRUCTURE.md), section 14; [data model audit](../../research/data-model-audit.md) | Rotate those credentials if they were ever real. Outside this plan. |
| R8 | **Fixed in configuration, as a proposal (2026-11-12; [ADR 0007](../../decisions/0007-users-cannot-edit-their-own-email.md), not yet confirmed by the owner).** A signed-in person could change their own email in Keycloak's account console, and the realm does not verify it again. The application copies the address from the token into the profile at every request and the import gives roles to the profile that holds an address, so a person could take the address of someone about to be imported with a role, or the bootstrap address, and receive that person's roles. Found by reading how `ProfileGate` and the import use the address and then looking at the running realm. | Observed before: the account console's email field was enabled and the realm's user profile had `edit: [admin, user]` for `email`. After: disabled (`identity.spec.ts`). `infra/keycloak/base/main.tf` | Needs the owner's confirmation. Not tried: a direct call to Keycloak's account API with a changed email |
| T12 | The production guard checks the issuer, the web address and the key addresses, but not the bootstrap address. A `.localhost` or `.test` address there would be invited and never delivered, so the first Super Admin would not arrive. Harmless to security (nobody can read that mail), a nuisance to an operator. | `platform/config/RequiredSettingsEnvironmentPostProcessor` | Accept; the runbook says to use a real address |

## What is not a finding

Several things look like gaps and are decisions: tokens carry no roles ([Kibootia ADR 0025](../../../kibootia-docs/decisions/0025-identity-and-authorization-data-split.md)), permissions are read on every request with no cache ([ADR 0044](../../../kibootia-docs/decisions/0044-authorization-enforcement-conventions.md)), and the browser holds no token ([ADR 0026](../../../kibootia-docs/decisions/0026-nextjs-bff-session.md)). They stay.
