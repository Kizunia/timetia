# Remove the example feature (Notes)

- **Type:** recipe
- **Module:** notes
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** observed (local run, 2026-11-11; no CI run yet). On a clean checkout the script removed 53 files and the tagged lines and passed its own leftover scan; the backend then built and passed 115 fast and 209 integration tests, and with the client regenerated from the rewritten `openapi.json` the frontend passed lint, type check and 171 unit tests. The frontend production build of that checkout was not run. The CI job `module-removal` runs the same steps on every pull request and has not run ([verification standard](../engineering/testing/verification-standard.md)).

## Problem

Notes is a working example, not part of what an application sells. A fork that has read [how to add a feature](../engineering/guides/add-a-feature.md) wants it gone: its code, its table, its screens and its permissions.

## When to use it, and when not to

Use it once you have no use for the example. Do not use it before you have copied the parts you want: the guide points at the files to read, and after removal they are only in your history.

It removes the module and what only exists for it. It does not touch `files`, `identity`, `access` or `audit`, which are not removable here. Removal is meant for a fresh fork. On a database that has already run the notes migrations, the `note` table stays; drop it by hand or with a migration of your own ([schema migrations](../architecture/data/schema-migrations.md)).

## Prerequisites and compatibility

Node 22 or later and a clean working copy (the script edits files; `git diff` shows what it did). Written for the repository as of milestone 5. The module's place in the technology catalogue is the "Example feature" row of the [catalogue](../technology/README.md).

## Steps

1. Run the script from the repository root:

   ```sh
   node scripts/remove-module.mjs notes
   ```

   `--dry-run` lists what it would do. It deletes the backend package and its tests, the module's migration folder, the frontend `features/notes` folder, the `app/notes` routes and the notes tests; takes the notes entry out of `frontend/src/modules.ts` and the notes lines out of the sign-in middleware, the problem messages and the audit filter; and removes the notes migration location from both `application.properties` files. Then it checks that no import, path or permission name of the module is left in the source and stops with a list if one is.

2. Build the backend. This rewrites `backend/openapi.json` without the notes endpoints:

   ```sh
   cd backend && ./mvnw verify
   ```

3. Regenerate the frontend client from it and build the frontend:

   ```sh
   pnpm --filter kibootia-frontend api:generate
   pnpm --filter kibootia-frontend lint
   pnpm --filter kibootia-frontend typecheck
   pnpm --filter kibootia-frontend test
   pnpm --filter kibootia-frontend build
   ```

4. Commit the result, including `backend/openapi.json` and `frontend/src/lib/api/schema.gen.ts`.

## Configuration and secrets

None. Notes has no switch of its own, because it needs no infrastructure ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)). Removing it is a change to the code, not to the configuration.

## Security

Removing the module removes its permissions (`notes.notes.*`) from the catalogue. Roles that held them keep the stored names, but a name the catalogue no longer declares is ignored when a user's permissions are resolved, so a removed permission grants nothing. Nothing else depended on them. The role editor rejects a name the catalogue does not know, so a role that still carries one needs it removed by hand in the database or a migration of your own.

## Testing and verification

The shared tests do not name the module. They use a stand-in feature of their own (`WidgetFixtures`), so deleting `notes` cannot break them. The `module-removal` CI job runs this recipe on a fresh checkout, then builds and runs every backend test and the whole frontend pipeline against what is left. The `ModuleStartupIT` group also checks that `/api/v1/notes` is simply absent (404).

## Observability

Nothing to change. The module's job `deleted-notes` and its event handlers go with it.

## Removing or replacing it

This is the removal. To bring the module back, restore it from version control.

## Related documents

[ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0052](../decisions/0052-milestone-5-implementation-decisions.md), [how to add a feature](../engineering/guides/add-a-feature.md), [disable files](disable-files.md), [disable observability](disable-observability.md).
