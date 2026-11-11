# Switch off the files module

- **Type:** recipe
- **Module:** files
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** observed (local run, 2026-11-11; no CI run yet). With `kibootia.files.enabled=false` set in the test, the application starts without storage keys, the module has no beans, and the avatar and note-picture endpoints answer 404 `files.disabled` (`FilesDisabledIT`, `NoteAttachmentFilesDisabledIT`). The CI job `module-removal` also starts the application with the switch set through the environment and runs the `startup` group; that has not been run locally ([verification standard](../engineering/testing/verification-standard.md)).

## Problem

An application with no use for avatars or uploads should not have to run an object store, or hold keys for one.

## When to use it, and when not to

Use it when nothing in your application keeps files. Do not use it to hide a storage outage: a module that is on and cannot reach its server answers 503 for file features and keeps everything else working ([ADR 0051](../decisions/0051-milestone-4-implementation-decisions.md)).

## Prerequisites and compatibility

Written for milestone 5. The module is a default-on infrastructure-backed module ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)); the catalogue row is "Object storage module".

## Steps

1. Set the switch in the environment of the backend, in the `.env` of a local checkout or the settings of a deployment:

   ```sh
   KIBOOTIA_FILES_ENABLED=false
   ```

2. Take `files` out of `COMPOSE_PROFILES` in `.env` so the object store is not started.
3. Restart the backend.

The storage keys (`KIBOOTIA_FILES_ACCESS_KEY`, `KIBOOTIA_FILES_SECRET_KEY`) are then not required.

## Configuration and secrets

`KIBOOTIA_FILES_ENABLED` (the property `kibootia.files.enabled`, default `true`). The names of the storage settings are in [application configuration](../operations/configuration/application-configuration.md).

## Security

With the module off there is no client, job, listener or permission of the module, and no file is written or served. Files stored earlier stay where they are and are not reachable.

## Testing and verification

What happens: none of the module's beans exist; `GET /api/v1/me` says `avatarsEnabled` and `filesEnabled` are false; the avatar endpoints and any feature's picture endpoints (such as a note's) answer 404 `files.disabled`; the user interface hides the controls; everything else works. Covered by `FilesDisabledIT`, `NoteAttachmentFilesDisabledIT` and `ModuleStartupIT`, and by the `files (switched off)` row of the CI job.

## Observability

Nothing is exported for the module. The reconciliation and purge jobs do not run.

## Removing or replacing it

To switch it on again, set the property to `true` (or remove it), add `files` back to `COMPOSE_PROFILES` and set the keys.

## Related documents

[ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0051](../decisions/0051-milestone-4-implementation-decisions.md), [start and check](../engineering/getting-started/start-and-check.md), [remove notes](remove-notes.md).
