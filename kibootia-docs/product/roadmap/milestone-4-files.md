# Milestone 4: Files

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** built; every criterion is covered by tests that passed in a local run on 2026-11-10 (103 fast and 238 integration backend tests, 168 frontend unit tests). The 8 end-to-end tests of the avatar story have not been run: they run first in CI, and this line becomes "verified" with the run ID once pull request #6 is green. Decisions made while building: [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md).

## Scope

The storage module and the avatar flow, as a complete vertical slice. Decision: [ADR 0029](../../decisions/0029-s3-object-storage-contract.md), extended by [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md) and [ADR 0051](../../decisions/0051-milestone-4-implementation-decisions.md).

## Completion criteria (approved by the owner on 2026-11-08 with the other milestones, OQ-49; limits chosen on 2026-11-10)

- The verification gate on the pinned RustFS passes (presigned PUT and GET and the other needed operations), or SeaweedFS becomes the local default (OQ-40).
- The storage port uses the S3 client, and contract tests run in CI against the local server.
- A user can upload, replace, view and delete an avatar. Type is detected from the bytes, size limits apply, images are re-encoded and stripped of metadata, and files stream through the backend and the Next.js proxy.
- Own avatars are always visible to their owner; viewing others' avatars requires `identity.profiles.view` within the tenant. Allowed and denied tests and tenant-isolation tests cover this.
- Uploads are rate limited with the `uploads` category of the limiter built in [milestone 3](milestone-3-identity-and-access-administration.md), and a test shows the 429.
- The reconciliation job and the purge job remove orphaned objects and expired deleted data, and erasing a user (`UserErased`) removes their stored files.
- The storage module can be switched off with `kibootia.files.enabled`, and CI proves the application still starts.
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).

## Status against the criteria

Level: observed. Every row passed in a local run on 2026-11-10; the CI run is pending.

| Criterion | Evidence |
|---|---|
| Gate on the pinned RustFS | `S3ObjectStorageContractIT`: 12 checks on `rustfs/rustfs:1.0.4`, including presigned PUT and GET, a tampered address and credentials of the generated shape. RustFS stays the local default |
| Port on the S3 client, contract tests against the local server | The same test; the adapter is `S3ObjectStorage` on the AWS SDK for Java. In CI it needs Docker, as the other Testcontainers tests do |
| Upload, replace, view, delete; type from the bytes; size limits; re-encoding; streaming | `AvatarApiIT` (a script named .png, GIF and SVG are 415; 5 MB plus 1 byte is 413; a damaged or empty image is 400; the stored objects hold none of the upload's comment or filename; two concurrent uploads leave one avatar), `ImageProcessorTest` (all three formats, EXIF orientation 1 to 8, metadata and trailing data removed, a 40-megapixel limit checked before decoding), `proxy-files.test.ts` (multipart streamed byte for byte, images streamed back, 304 passed through), `avatar.test.tsx`, and the `avatar` end-to-end story (pending CI) |
| Own avatar always visible; others need `identity.profiles.view`; tenant isolation | `AvatarApiIT` (owner, an unverified owner, a user without and with the permission, an administrator, another tenant's profile and file, a cached copy that does not get past a refusal) |
| Uploads rate limited, a test shows the 429 | `AvatarRateLimitIT` (the third upload within an allowance of two is 429 with `Retry-After`, stores nothing, does not affect another user, and a refused caller uses up no allowance) |
| Reconciliation and purge remove orphans and expired data; erasing a user removes their files | `FileJobsIT` (purge in batches and with the storage server down, reconciliation with its grace period and its report-only rule, erasing a user removes every file they own and no one else's) |
| Module switched off, the application still starts | `FilesDisabledIT` (starts with `kibootia.files.enabled=false` and no keys, none of the module's beans exist, the avatar endpoints answer 404 `files.disabled`, the rest works), `RequiredSettingsTest`, ArchUnit rule 5 |
| All of the above in CI | Pull request #6, run pending |

## Deviations and limits

- **No CI job runs every optional module disabled yet.** `FilesDisabledIT` is the check for `files` and runs with the backend tests; the job that builds and starts the application with each optional module removed comes with [milestone 5](milestone-5-example-feature.md).
- **Replaced and removed avatars stay in storage for the retention period** (30 days), hidden at once, as [ADR 0034](../../decisions/0034-soft-delete-with-retention-purge.md) says. Erasing a user removes everything at once.
- **Users change only their own avatar.** An administrator cannot remove someone else's; that would be a permission and an audited action of its own.
- **Presigned transfers exist in the port and no category uses them.** The gate showed they work on RustFS.
- **The multipart parser runs before the rate limit**, so a user past their allowance can still send up to 5 MB per attempt. The production edge limit covers it.
- **Only image categories exist.** A category of another kind needs its own type rules and processing.
- **Local environments made before this milestone** have no `files` in `COMPOSE_PROFILES`: `pnpm bootstrap` adds the two keys, and the profile is added by hand ([start and check](../../engineering/getting-started/start-and-check.md)). Until then an upload answers 503.
