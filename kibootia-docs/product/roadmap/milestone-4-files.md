# Milestone 4: Files

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned.

## Scope

The storage module and the avatar flow, as a complete vertical slice. Decision: [ADR 0029](../../decisions/0029-s3-object-storage-contract.md), extended by [ADR 0041](../../decisions/0041-user-lifecycle-and-account-model.md).

## Completion criteria (proposed; rewritten on 2026-11-08 after the second decision round, awaiting owner approval)

- The verification gate on the pinned RustFS passes (presigned PUT and GET and the other needed operations), or SeaweedFS becomes the local default (check OQ-40).
- The storage port uses the S3 client, and contract tests run in CI against the local server.
- A user can upload, replace, view and delete an avatar. Type is detected from the bytes, size limits apply, images are re-encoded and stripped of metadata, and files stream through the backend and the Next.js proxy.
- Own avatars are always visible to their owner; viewing others' avatars requires `identity.profiles.view` within the tenant. Allowed and denied tests and tenant-isolation tests cover this.
- The reconciliation job and the purge job remove orphaned objects and expired deleted data.
- The storage module can be switched off with `kibootia.files.enabled`, and CI proves the application still starts.
- All of the above pass in CI ([verification standard](../../engineering/testing/verification-standard.md)).
