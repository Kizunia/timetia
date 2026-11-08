# 0029 S3 API object storage contract, RustFS locally

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. There is no storage service or storage code.

> **Extended by [ADR 0041](0041-user-lifecycle-and-account-model.md)**: viewing another user's avatar also requires the `identity.profiles.view` permission; same-tenant remains the outer limit. This decision still holds.

## Context

The ideation brief selected MinIO as the default object store for profile photos and other uploads. Research on 2026-10-28 found that the MinIO community repository was archived on 2026-04-25 and its README says it is no longer maintained; the community edition is distributed as source only, and the free successor (AIStor Free) is proprietary. A template that ships an unmaintained storage server as its default would carry that risk into every fork. The owner asked that the choice be easy to run locally and easy to deploy, and delegated the pick to the architect.

## Decision

The **contract is the S3 API**. The storage port uses the standard AWS SDK for Java S3 client. Endpoint, region, credentials and bucket come from configuration.

- **Local default:** RustFS (Apache 2.0), one container with access-key and secret-key environment variables and a web console. Pin an exact image version, never `latest`.
- **Production:** managed S3-compatible storage is the first recommendation. Self-hosting is a recipe; SeaweedFS is the recommended self-hosted server because of its maturity.
- **Gate:** before this is final, a verification spike must confirm presigned PUT and GET and the other needed operations against the pinned RustFS. If it fails, the local default becomes SeaweedFS. Presigned-URL support was not confirmed in the vendors' own documentation.
- **Mitigation:** CI runs storage contract tests against the S3 API, so changing server is a configuration change.
- Storage is an infrastructure-backed module ([ADR 0024](0024-package-modules-with-infrastructure-toggles.md)): default-on, with a toggle and its own Compose profile.

**Files**

- Uploads and downloads stream browser, Next.js, Spring, storage by default. Storage stays on the private network with no public endpoint or bucket CORS. The port also supports presigned transfers for file categories that opt in; those need a quarantine-and-confirm step and a public storage endpoint.
- Avatars are visible to signed-in users of the same tenant, decided through the authorization decision point. There is no public bucket or route.
- Mandatory: content type detected from the bytes (not the filename), per-category allow-lists, configurable size limits.
- Shipped: avatars are re-encoded to fixed sizes with metadata (such as EXIF) removed, and a reconciliation job finds objects without metadata rows and the reverse. Malware scanning (ClamAV) is a recipe behind a no-op inspection hook.
- Object keys are tenant-prefixed ([ADR 0028](0028-tenant-ready-schema.md)). Metadata and ownership live in the relational database.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| MinIO community edition, pinned or via a community fork | No upstream security fixes for a template default |
| MinIO AIStor Free | Proprietary licence |
| SeaweedFS as the local default | Mature, but credentials need a config file or shell commands, which is less easy locally; kept as the self-hosting recipe and the fallback |
| Garage | Deliberately narrow S3 API (no versioning or lifecycle rules) and AGPLv3 |
| Presigned URLs by default | Needs a publicly reachable store and bucket CORS, and validation happens after upload |

## Consequences

RustFS reached version 1.0 only in September 2026, so it is young; the pin, the contract tests and the SeaweedFS fallback limit the risk. This ADR replaces the brief's MinIO choice with the owner's delegated agreement; MinIO is recorded in the catalogue as considered and not selected.

## Status

accepted (the verification gate is open). Avatar visibility extended by [ADR 0041](0041-user-lifecycle-and-account-model.md).

## Related

[Technology catalogue](../technology/README.md), [ADR 0024](0024-package-modules-with-infrastructure-toggles.md), [ADR 0027](0027-central-authorization-decision-point.md).
