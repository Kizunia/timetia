# Threat Model

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** the entries for milestones 1 and 2 are verified or observed as their rows in [implementation status](../../product/roadmap/implementation-status.md) say; the entries added for milestone 3 (marked below) are verified (CI run 42780048416, 2026-11-10); the entries marked milestone 4 are verified (CI run 42873216224, 2026-11-10). Entries for later milestones are intended mitigations only.

A living, short threat model organised by trust boundary (owner decision of 2026-10-28). Each capability adds its entries when it is built, and a milestone that adds a boundary is not complete until its entries exist. It lists what could go wrong and the intended mitigation, with the decision that fixes it. It is a starting point, not a completed analysis.

## Boundaries

```mermaid
flowchart LR
    browser([Browser]) -->|session cookie| bff[Next.js server<br/>BFF]
    bff -->|bearer token| api[Spring backend]
    bff -->|OIDC| kc[Keycloak]
    api -->|Admin API, service account| kc
    kc --> kcdb[(PostgreSQL)]
    tofu[OpenTofu step] -->|realm configuration| kc
    api --> db[(MySQL)]
    api --> s3[(S3-compatible storage)]
    api -->|OTLP| otel[Telemetry backend]
```

## Entries

| Boundary | Threat | Intended mitigation | Decision |
|---|---|---|---|
| Browser to BFF | Script injection steals credentials | Tokens never reach the browser; HttpOnly, Secure, SameSite cookie; nonce-based content security policy | [0026](../../decisions/0026-nextjs-bff-session.md) |
| Browser to BFF | Cross-site request forgery on cookie-authenticated changes | Matching `Origin` header plus a required custom header on every state-changing request, with a SameSite cookie | [0042](../../decisions/0042-bff-api-proxy-and-csrf.md) |
| Browser to BFF | The `/api` proxy is abused to reach other hosts or leak cookies | Fixed upstream (Spring `/api` only); cookies and hop-by-hop headers stripped; dedicated proxy tests | [0042](../../decisions/0042-bff-api-proxy-and-csrf.md) |
| Browser to BFF | Session cookie tampering or theft | Encrypted cookie; short access-token lifetime; documented upgrade to a server-side store for revocation | [0026](../../decisions/0026-nextjs-bff-session.md) |
| BFF to Spring | Token meant for another client is accepted | Signature, issuer, expiry and audience checks | [0007](../../decisions/0007-spring-security-resource-server.md), [0008](../../decisions/0008-audience-validation-and-deny-by-default.md) |
| BFF to Spring | A new endpoint is exposed unprotected | Deny-by-default; no public endpoint on the API port; health on an internal management port | [0008](../../decisions/0008-audience-validation-and-deny-by-default.md), [0047](../../decisions/0047-health-exposure-and-container-images.md) |
| BFF to Spring | A machine token is used to act without a user | Service-account tokens are rejected | [0044](../../decisions/0044-authorization-enforcement-conventions.md) |
| Spring to Keycloak keys | Signing keys cannot be fetched | Use cached keys; with none, refuse requests | [token validation](../../architecture/identity/token-validation.md) |
| Spring to Keycloak Admin API | The service account is abused or leaked | Least-privilege realm-management roles, never realm-admin (a test shows a client with fewer roles is refused); secret from environment or file and never in an error message; administrative actions audited; its tokens are rejected by the API | [0025](../../decisions/0025-identity-and-authorization-data-split.md), [0050](../../decisions/0050-milestone-3-implementation-decisions.md) |
| Authorization | Privilege escalation through role editing | Protected admin role; last administrator cannot be removed; every change audited | [0025](../../decisions/0025-identity-and-authorization-data-split.md) |
| Authorization | A check is missing on one operation | One decision point; checks on use cases; an ArchUnit rule requires a check or an explicit exemption; allowed and denied tests for every protected operation | [0027](../../decisions/0027-central-authorization-decision-point.md), [0044](../../decisions/0044-authorization-enforcement-conventions.md) |
| Authorization | A disabled user keeps working with a still-valid session | Sessions ended in Keycloak; the backend checks profile status on every request | [0041](../../decisions/0041-user-lifecycle-and-account-model.md) |
| Authorization | Someone registers with the bootstrap administrator's address and is made administrator (milestone 3) | The grant needs a verified address in the token, only while no administrator exists; it is audited | [0050](../../decisions/0050-milestone-3-implementation-decisions.md) |
| Authorization | An administrator, or a role editor, grants more than they hold (milestone 3) | A role can only be given permissions its editor holds; only an administrator gives the administrator role; nobody changes their own account; the last administrator who can sign in cannot be removed, even concurrently | [0050](../../decisions/0050-milestone-3-implementation-decisions.md) |
| Authorization | An unverified account does anything beyond viewing itself (milestone 3) | The decision point returns no permissions until the token says the address is verified | [0041](../../decisions/0041-user-lifecycle-and-account-model.md) |
| Accounts | An old token of an erased user recreates their profile (milestone 3) | A user not yet known is confirmed with Keycloak first, and erasure deletes the Keycloak account before the profile | [0050](../../decisions/0050-milestone-3-implementation-decisions.md) |
| Accounts | Mass or fake self-registration, accounts in someone else's name | Email verification required before use; Keycloak brute-force detection; rate limits; production review of bot protection; new users see no other users by default | [0041](../../decisions/0041-user-lifecycle-and-account-model.md) |
| Data | One tenant reads another's data | `tenant_id` with a Hibernate discriminator; native queries reviewed; isolation tests in CI | [0028](../../decisions/0028-tenant-ready-schema.md) |
| Data | Soft-deleted personal data is retained or leaks through queries | Soft-delete filtering with tests; retention purge; immediate erasure on request | [0034](../../decisions/0034-soft-delete-with-retention-purge.md) |
| Data | Identifiers are guessed | UUIDv7 identifiers | [0033](../../decisions/0033-uuidv7-identifiers.md) |
| Uploads | Malicious or disguised file (milestone 4) | Type found from the bytes and checked against an allow-list of JPEG, PNG and WebP (SVG is left out because it can carry script); the filename and declared type are never read; the size limit is applied while reading; every image is re-encoded into new PNGs, so nothing hidden in the original survives; an inspection hook where a malware scan plugs in (a recipe); the stored image is served with `nosniff` and a `default-src 'none'; sandbox` policy | [0029](../../decisions/0029-s3-object-storage-contract.md), [0051](../../decisions/0051-milestone-4-implementation-decisions.md) |
| Uploads | A small file that decodes to gigabytes of memory (milestone 4) | The image's size is read from its header and an image over 40 megapixels is refused before it is decoded; a large one is read with subsampling; the upload is at most 5 MB | [0051](../../decisions/0051-milestone-4-implementation-decisions.md) |
| Uploads | A photo's metadata, such as the location, reaches other users (milestone 4) | EXIF, GPS, embedded thumbnails, comments and trailing data are not in any stored rendition; tests plant each and look for them in storage | [0051](../../decisions/0051-milestone-4-implementation-decisions.md) |
| Storage | Files read without authorization (milestone 4) | Storage on the private network, its ports bound to the loopback address locally; no public bucket or link; files streamed through the backend; every view checked by the decision point (`identity.profiles.view` for others' avatars, the tenant as the outer limit); a cached copy is revalidated, so a withdrawn permission takes effect at once | [0029](../../decisions/0029-s3-object-storage-contract.md), [0051](../../decisions/0051-milestone-4-implementation-decisions.md) |
| Storage | A note's picture is read or changed by someone who may not see or change the note (milestone 5) | The picture follows the note and not the file: every view and change goes through the note's decision, so a note the caller may not see is 404 for its picture too, one they may see but not change is 403, and a note with no picture answers like one that cannot be seen; the file is served only through the note's endpoint, with a check on every view; a deleted note hides its picture; the upload allowance is spent after the permission check, so a refused caller costs nothing | [0052](../../decisions/0052-milestone-5-implementation-decisions.md) |
| Storage | A file outlives its record or its owner (milestone 5) | The file belongs to the note's owner, whoever uploaded it, so erasing the owner removes it; purging a note publishes a durable event that discards the file, so a storage outage delays the removal and never loses it or undoes the purge; a deleted note keeps its picture hidden until the purge, so a restore is complete | [0052](../../decisions/0052-milestone-5-implementation-decisions.md) |
| Storage | The storage keys are leaked or left at a default (milestone 4) | Generated per developer into the untracked `.env`, never in `application.properties`; startup fails naming the missing key while the module is on; an error from the storage client names no credential | [0032](../../decisions/0032-secrets-from-environment-and-files.md), [0051](../../decisions/0051-milestone-4-implementation-decisions.md) |
| Storage | A storage outage takes the application down, or leaves data out of step (milestone 4) | The module affects only file features (readiness checks MySQL only); objects are written before the row and removed again on failure; daily reconciliation removes objects nothing refers to once they are old enough and only reports rows without objects | [0047](../../decisions/0047-health-exposure-and-container-images.md), [0051](../../decisions/0051-milestone-4-implementation-decisions.md) |
| Abuse | Brute-force login, invite spam, verification-mail floods, upload floods | Keycloak brute-force detection; in-app per-user rate limiter on invitations, verification mail, administrative actions and uploads (the multipart parser runs before it, so a user past their allowance can still send up to 5 MB per attempt); edge limiting required in production | [API conventions](../../architecture/api/conventions.md) |
| Secrets | Development credentials reach production | Generated local secrets; production refuses to start with development defaults | [0032](../../decisions/0032-secrets-from-environment-and-files.md) |
| Secrets | Committed credentials | Secret scanning in CI; credentials ever committed are rotated | [secrets management](secrets-management.md) |
| Operations | Sensitive data in logs or telemetry | Logs and traces exclude tokens and secrets | [observability baseline](../monitoring/observability-baseline.md) |
| Operations | Local observability stack exposed | The all-in-one container is for development only | [0030](../../decisions/0030-otlp-telemetry-and-local-lgtm.md) |
| Operations | Keycloak admin console attacked from the internet | Blocked at the proxy; reached only through an SSH tunnel | [0045](../../decisions/0045-keycloak-operations.md) |
| Operations | OpenTofu state leaks secrets | State stored privately and encrypted (S3 bucket) or as a root-only file; never committed | [0045](../../decisions/0045-keycloak-operations.md) |
| Data | Personal data retained in the audit log after erasure | Audit entries reference people by ID only | [0046](../../decisions/0046-audit-log-design.md) |

## Not yet covered

Supply-chain risks beyond dependency and image scanning, backup confidentiality, and the production telemetry backend. They are added with the milestone that introduces them ([milestone 6](../../product/roadmap/milestone-6-production-readiness.md)).
