# Milestone 4: Files

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned.

## Scope (owner decision, [ADR 0029](../../decisions/0029-s3-object-storage-contract.md))

The storage module and the avatar flow, as a complete vertical slice.

## Completion criteria (proposed, not yet approved)

- The verification gate on the pinned RustFS passes (presigned PUT and GET and the other needed operations), or SeaweedFS becomes the local default ([open questions](../../decisions/open-questions.md)).
- The storage port uses the S3 client, and contract tests run in CI against the local server.
- A user can upload, replace, view and delete an avatar. Type is detected from the bytes, size limits apply, images are re-encoded and stripped of metadata, and files stream through the backend.
- Authorization (own avatar, same-tenant viewing) and tenant isolation have allowed and denied tests.
- The reconciliation job and the purge job remove orphaned objects and expired deleted data.
- The storage module can be switched off, and CI proves the application still starts.
- All of the above pass in CI.
