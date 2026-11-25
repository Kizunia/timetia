# 0051 Milestone 4 implementation decisions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-10

> **Implementation:** verified (CI run 42873216224, 2026-11-10, pull request #6): 103 fast and 238 integration backend tests (including the storage contract against a real RustFS), 168 frontend unit tests with lint, type check and build, the image scan, the secret scan, and 22 end-to-end tests against the full Compose stack, 8 of them the avatar story ([verification standard](../engineering/testing/verification-standard.md)).

## Context

[Milestone 4](../product/roadmap/milestone-4-files.md) built what [ADR 0029](0029-s3-object-storage-contract.md) and [ADR 0041](0041-user-lifecycle-and-account-model.md) decide: object storage behind the S3 API, and the avatar as a complete vertical slice. Those decisions fix the contract (S3 API, RustFS locally, streaming through the backend, type from the bytes, re-encoding) but leave open how the module is reached without breaking the optional-module rule, how a failure between storage and database is repaired, and a number of details that only became visible while building. The owner chose three limits for the slice: JPEG, PNG and WebP up to 5 MB, replaced and removed avatars kept for the retention period like any deleted data, and users managing only their own avatar.

## Decision

**The verification gate (OQ-40) passed, so RustFS stays the local default.** `S3ObjectStorageContractIT` runs 12 checks against `rustfs/rustfs:1.0.4`: put and get (including an empty object and a 2 MB one), overwrite, a missing key, delete, batch delete, paged listing by prefix, presigned PUT and presigned GET over plain HTTP, a tampered presigned address, wrong credentials and an unreachable server. The credentials in that test have the shape `pnpm bootstrap` generates (43 URL-safe characters with `-` and `_`). The pin is 1.0.4: the newer 1.0.5 tag is a preview. The same tests would run against SeaweedFS by changing the container, which is the fallback ADR 0029 keeps.

**The AWS SDK needs two settings against a non-AWS server.** The SDK adds a trailing checksum to every upload and checks one on every download by default; servers that do not implement it refuse the request. Both are set to "when required". Path-style addressing is used because a local server has no wildcard DNS. The JDK-based HTTP client replaces the Netty and Apache clients, which keeps two HTTP stacks out of the application. RustFS accepted the SDK's presigned requests, but no category uses presigned transfers yet: the port has them because ADR 0029 makes them opt-in, and a category that opts in needs the quarantine step and a public storage address ADR 0029 describes.

**A port in `platform`, an implementation in `files`, and the consumer asks for it through an `ObjectProvider`.** Architecture rule 5 says core code does not depend on an optional module, and `identity` is core. `platform.files.StoredFiles` (with `FileCategory`, `StoredFileRef` and `FileContent`) is the port; `files` implements it only while `kibootia.files.enabled` is not `false`; `identity` asks for it with `ObjectProvider<StoredFiles>` and answers 404 `files.disabled` when there is none. `files` depends on `identity` for one thing, the `UserErased` event, so the module dependency points one way. ArchUnit rule 5 is implemented for `files`: nothing outside the module may depend on it. Rule 1 now also forbids `application` code from depending on the `s3` adapter package.

**Objects first, then the row, and never a database transaction around a storage call.** An upload writes every rendition to storage and then inserts the metadata row in its own short transaction. A failure between the two leaves objects nothing refers to; a failure writing them removes what was written. The key of an object is `<tenant>/<category>/<file ID>/<rendition>`, built by the module and never from user input, and the file ID is a UUIDv7 made before the row exists (the store hands it out, because application code may not use the persistence helper). The two jobs that call storage in bulk do hold a transaction (the retention purge runs inside the retention job's); they delete objects before rows, so a failure leaves rows that name missing objects and the next run finishes the job.

**Every upload passes the same four steps.** The size is limited while reading, so an oversized upload is never held in memory: the lower of the category's limit and `kibootia.files.max-upload-size` (5 MB). The type is found from the bytes with Apache Tika's detector (`tika-core` 3.3.3; the 4.x line has no patch release yet, [ADR 0035](0035-version-adoption-policy.md)) and the filename and declared type are never read. The allowed types are JPEG, PNG and WebP; SVG is left out on purpose because it can carry script. Then the inspection hook runs (`FileInspector`, a bean that accepts everything unless a deployment replaces it, which is where the malware scan recipe plugs in). Finally the image is re-encoded.

**Re-encoding makes a new image, so nothing of the original survives.** The decoder is asked for the size first and refuses an image over 40 megapixels before decoding it (the usual way a small file becomes gigabytes of memory); a larger image is read with subsampling. The EXIF orientation is read and applied before anything else, because stripping the metadata would otherwise leave every phone photo on its side. The image is cut to its centred square and scaled to 64 pixels (`small`) and 256 pixels (`large`) as PNG, drawn onto a new canvas, so EXIF, GPS position, embedded thumbnails, comments and data appended after the image end are gone. Tests plant each of those in the upload and look for them in the stored objects. WebP is read through TwelveMonkeys (`imageio-webp`, `imageio-jpeg` 3.15.3), and `ImageIO.scanForPlugins()` runs where the class loads, because Spring Boot's launcher loads libraries through its own class loader.

**Showing an avatar checks permission on every view.** `GET /api/v1/users/{id}/avatar?size=SMALL|LARGE` is served by the backend and the Next.js proxy, never from a public bucket or a long-lived link. A user always sees their own avatar, verified or not. Anyone else's needs `identity.profiles.view`; without it the answer is 403, the same as for `GET /users/{id}`, so the status says nothing about whether the user exists. A user with no avatar, an unknown or deleted user and another tenant's user all answer 404. The response is `image/png` with `X-Content-Type-Options: nosniff`, a `Content-Security-Policy` of `default-src 'none'; sandbox`, an `ETag` made from the file ID and size, and `Cache-Control: private, no-cache`: the browser may keep a copy, but asks before using it, which costs the permission check and not the transfer. A cached copy therefore never overrides a permission that was taken away. `avatarVersion` (the file ID) in `/me` and in a user's representation changes with every upload and is the cache-buster in the image address. The frontend draws the image with a plain `<img>`, not `next/image`: the optimizer fetches without the session cookie and would keep a copy for everyone.

**Changing one's own avatar needs a verified address and uses the upload limit.** Upload and removal call `requireVerified` first (like changing a preference) and uploads then take a token from the `uploads` bucket of the rate limiter of [ADR 0050](0050-milestone-3-implementation-decisions.md), after the authorization check, so a refused caller uses up nobody's allowance. The profile row is locked for the switch of the avatar, so two uploads at the same moment leave exactly one avatar and each hides the file it replaced; a test runs five rounds of two concurrent uploads.

**What is deleted when.** Replacing or removing an avatar hides the file at once (soft delete) and the retention purge removes it, objects and row, after the retention period (30 days), exactly as [ADR 0034](0034-soft-delete-with-retention-purge.md) says; there is no exception. Erasing a user removes every file they own at once, deleted ones included, from the `UserErased` event, with the objects deleted before the rows. A user's own avatar column has no foreign key, because the files module owns its table and may be switched off.

**Reconciliation removes objects, and only reports rows.** Daily, under a database lock, for each tenant: an object whose file has no row is deleted once it is older than `kibootia.files.reconcile-grace` (24 hours), so an upload being written is never touched; a row older than the grace period whose objects are missing is only logged. Metadata is never deleted on the word of a listing, because a partial answer or an outage would erase the record of a file that is fine. Keys that do not follow the key shape, and other tenants' prefixes, are ignored.

**Switching the module off, and starting without storage.** `@ConditionalOnFilesEnabled` guards every bean of the module, so with `kibootia.files.enabled=false` there is no client, no job, no listener and no permission, and the application starts without storage settings. The table stays migrated, so schema validation passes. The storage keys are required at startup only while the module is on, and the message names the setting. The bucket is created at startup when asked to, and again on the first upload if the server was down then, so a storage outage at startup only affects file features ([ADR 0047](0047-health-exposure-and-container-images.md)). `FilesDisabledIT` is the module-removal check for `files`; the CI job that runs it for every module comes with milestone 5.

**RustFS runs in its own Compose profile.** `rustfs` is in the `files` profile, bound to the loopback address (the backend reaches it over the Compose network; browsers never do), on a named volume, with a health check on `/health`. `.env.example` now starts `observability,files`, and the backend's dependency on it is optional so the stack starts without the profile. An existing `.env` keeps its old `COMPOSE_PROFILES`, because `pnpm bootstrap` never changes a value: it adds the two new keys, and the developer adds `files` by hand, which [the guide](../engineering/getting-started/start-and-check.md) says. Until then uploads answer 503 and everything else works.

**Two limits worth knowing.** The multipart parser runs before the use case, so a user past their upload allowance can still send up to 5 MB per attempt before the 429; the edge limit that [the API conventions](../architecture/api/conventions.md) require in production covers this. And an upload larger than the multipart ceiling (6 MB) is answered by Spring only because Tomcat is told to read and discard up to 16 MB of a refused upload; without that the browser would see a reset connection.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| `identity` depends on `files` directly | Core code would depend on an optional module and the rule 5 test could not exist |
| Presigned upload for avatars | Needs a public storage address and a quarantine step; the file would be checked after it arrived ([ADR 0029](0029-s3-object-storage-contract.md)) |
| Keep the original and render on demand | Keeps the metadata the re-encoding exists to remove, and puts image decoding on the read path |
| Delete a replaced avatar at once | Needs an exception to [ADR 0034](0034-soft-delete-with-retention-purge.md); the owner chose the retention period |
| Delete rows whose objects are missing | One partial listing could erase the record of files that are fine |
| `next/image` for avatars | The optimizer fetches without the cookie and caches one copy for everyone |
| A public, long-cached avatar address | Permission could not be withdrawn until the cache expired |
| The shadcn avatar component | Radix's image waits for the load and is awkward to test; the plain component also needed no new dependency |

## Consequences

- A module that keeps files declares a `FileCategory`, calls `StoredFiles`, and stores the returned ID next to its own data. It authorizes first; the files module only holds bytes.
- A new image category needs no change to the files module. A category of another kind (documents, for example) needs the type rules and the processing extended.
- Each avatar upload is held in memory once (at most 5 MB) and the decoded image at most 40 megapixels, subsampled above 4.
- Moving to another S3 server is a change of `kibootia.files.endpoint` and the keys, checked by the contract test.

## Status

accepted. Extends [ADR 0029](0029-s3-object-storage-contract.md) (its verification gate passed locally and in CI run 42873216224, 2026-11-10) and [ADR 0041](0041-user-lifecycle-and-account-model.md), which stay accepted. Applies [ADR 0034](0034-soft-delete-with-retention-purge.md) without an exception.

## Related

[Milestone 4](../product/roadmap/milestone-4-files.md), [threat model](../operations/security/threat-model.md), [API conventions](../architecture/api/conventions.md), [application configuration](../operations/configuration/application-configuration.md).
