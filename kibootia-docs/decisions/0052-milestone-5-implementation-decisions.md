# 0052 Milestone 5 implementation decisions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** observed (local run, 2026-11-11; no CI run yet): 135 fast and 269 integration backend tests, and 196 frontend unit tests with lint, type check and build. One rate-limit test failed in that full run, was fixed, and passed when re-run with the class it had interfered with. With the notes module removed by the script, 115 fast and 209 integration backend tests pass, and the regenerated client, lint, type check and 171 unit tests pass. The `startup` group passed with its default switches; with each switch set through the environment, the stripped-tree production build and a second full run could not be done locally (Docker stopped). The 31 end-to-end tests are written and type-checked and need the full stack, so they have not run. Not verified until a CI run covers it ([verification standard](../engineering/testing/verification-standard.md)).

## Context

[Milestone 5](../product/roadmap/milestone-5-example-feature.md) turns the backend-only `notes` module of [ADR 0049](0049-milestone-2-implementation-decisions.md) into the reference feature applications copy, and adds the check that a fork can remove it. [ADR 0024](0024-package-modules-with-infrastructure-toggles.md) says what must be true (a documented removal recipe per removable module, CI that builds and starts the application with each one removed or off) and [ADR 0051](0051-milestone-4-implementation-decisions.md) says how a module keeps a file. Neither says how a picture that keeps its proportions fits a files module built for square avatars, how a feature's file follows the feature's own life (delete, restore, purge, erase), what the screens need from the shell, or how a test suite can stay green when a module is deleted from under it. The owner chose on 2026-11-11: the name stays "Notes"; a note has one picture, kept whole and not cropped; and the CI matrix has four rows (notes removed, files off, observability off, all three).

## Decision

**The example feature keeps the name Notes.** It is small, generic and has no business meaning to leave behind in a fork. OQ-37 is closed.

**A rendition has a shape.** `FileCategory.Rendition` gains `Shape.SQUARE` (the centred square, scaled to the size, enlarged if the image is smaller: an avatar) and `Shape.FIT` (the whole image, its longest side the size, never enlarged). The two-argument constructor stays square, so the avatar category did not change; `Rendition.fit(name, pixels)` makes the other kind. Orientation, re-encoding, metadata removal and the 40-megapixel limit are shared, and the same tests plant EXIF, GPS, comments and trailing data in an upload and look for them in a fit rendition. A note's picture is the category `note-image` with `preview` (480) and `full` (1600), both fit, at most 5 MB.

**One picture per note, kept as an ID with no foreign key.** `note.attachment_file_id` holds the stored file, as `user_profile.avatar_file_id` does; the files module owns its table and can be off. A second picture would be a child table, a limit per note and a list endpoint, which is a different pattern from the one this milestone shows. The entity maps the column `updatable = false`, and the column is changed by a statement of its own that names the tenant. That does two things: an ordinary edit of the title can never write back a picture that changed while the editor was open, and attaching a picture does not change the note's `version`, so someone typing in an open editor can still save after an upload. The replacement locks the note row (`PESSIMISTIC_WRITE`) so two uploads at the same moment each hide the file the other left, and exactly one remains.

**The picture follows the note, and the file belongs to the note's owner.** Whoever may see a note may see its picture, checked on every view; whoever may change a note (`notes.notes.update`, or `update-any` for another's) may attach, replace or remove it. A note the caller may not see is 404 for every attachment operation, a note without a picture is 404 `notes.attachment_not_found`, one they may see but not change is 403. The upload allowance of [ADR 0050](0050-milestone-3-implementation-decisions.md) is spent after those checks, so a refused caller uses up nothing. The stored file is owned by the note's owner, not by whoever uploaded it: the files module erases every file a user owns on `UserErased`, and an administrator who attached a picture to someone else's note must not take it away when they are erased themselves.

**A deleted note keeps its picture, hidden with it, and the purge removes it through an event.** Soft delete hides the note, and with it the endpoint that serves the picture, so nothing needs to touch the file; restoring the note brings the picture back, which the files module (it has no un-discard) could not do after a discard. The retention purge publishes `NotePurged(noteId, attachmentFileId)` in its own transaction for each purged note that had a picture, before it deletes the rows, and `NoteEvents` discards the file from a `@DurableEventListener`. A storage server that is down delays the removal, retries with the back-off of [ADR 0031](0031-transactional-outbox-and-locked-jobs.md), and neither undoes the purge nor loses the file. With files switched off the handler logs and leaves the file alone. Erasing the owner needs nothing new: `NoteEvents` removes the notes, `FileEvents` the files.

**Deleted notes can be listed, so one can be found and restored.** `GET /api/v1/notes?deleted=true` needs `notes.notes.restore` (403 without it) and lists everyone's deleted notes of the tenant, with the same text and owner filters and the same allow-listed sort. Entity queries hide deleted rows, so the statement is native and names the tenant; it is built by hand with the sort field going through a fixed list of columns and every filter bound, because Spring Data's sorting of native queries is not something this milestone should depend on.

**`/me` says whether files are on.** `filesEnabled` sits beside `avatarsEnabled` and means the same thing for any feature that keeps files, so the notes screen does not borrow the avatar's name. Both come from `ObjectProvider<StoredFiles>`, so `identity` still does not depend on `files`.

**The frontend learns about removable features from one list.** `src/modules.ts` holds each feature's navigation link and protected paths, between `module:<name>:start` and `module:<name>:end` lines; the header and the sign-in middleware read it. The Next.js `matcher` must be a literal, so a feature's paths are also on one tagged line in `src/proxy.ts`. Lines that exist only because a module does (its problem messages, an audit filter option) are tagged `module:<name>`. Removing a module is therefore deleting its folders and its tagged lines, which `scripts/remove-module.mjs` does and the recipe describes.

**Shared tests do not name a removable module.** The tests of `platform`, `access` and `identity` had used `notes.notes.*` and `/api/v1/notes` as a convenient set of real permissions and a protected URL. They now use `WidgetFixtures` (a `PermissionProvider` and two endpoints in the test sources, imported by `AdminApiTest` and never by the test that writes `openapi.json`), `AdminApiTest` no longer clears the `note` table (the test that creates notes clears it), `ModularityTest` requires only the core modules, and `OpenApiContractIT` leaves the notes paths to `NotesOpenApiIT`. The erase-removes-notes check moved to `NoteErasureIT`. After these changes deleting the module breaks no test outside it.

**Architecture rules are shown to fire.** Rule 5 is a reusable rule (`ModuleRules`) applied to every module in `REMOVABLE_MODULES` (`files`, `notes`). `ArchitectureRulesFireTest` points rules 5, 7, 9, 10 and 11 at classes in the test sources made to break them and at classes made to satisfy them. A rule that matches nothing, which several of them allow so that they hold before the module exists, therefore cannot pass unnoticed. Spring Modulith, which owns rules 2 and 3, analyses only main classes, so a broken module cannot be fed to it from the test sources (an attempt failed with "No classes found"); `ModularityTest` instead asserts that the real core modules depend on each other and that `platform` depends on none, so the verification has edges to check and the cycle and access rules are not vacuous.

**CI proves removal with a four-row matrix.** The job `module-removal` runs on every pull request: `notes (removed)` applies the script and then runs every backend test and the whole frontend pipeline against what is left (the client is regenerated from the `openapi.json` that build wrote, and the drift check is skipped because the file is expected to change); `files (switched off)`, `observability (switched off)` and the three together set the switches in the environment and run the tests tagged `startup`: the migrate-from-empty test and `ModuleStartupIT`, which starts the application against MySQL, checks the core API, checks that `avatarsEnabled` and `filesEnabled` follow the files switch and the avatar endpoint says `files.disabled`, and that `/api/v1/notes` is either served or simply absent (404). `KIBOOTIA_FILES_ENABLED` and `KIBOOTIA_OBSERVABILITY_ENABLED` are in `.env.example`.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Square renditions for the picture, as for the avatar | No change to the files module, but the example would crop every picture and teach a pattern nobody should copy |
| A file category for documents | Needs type rules, original filenames and download headers, and a threat-model entry of its own; a milestone of its own |
| Several pictures per note | A child table, a limit and a list endpoint: twice the work for a pattern the owner did not ask for |
| Bump the note's version when the picture changes | An upload would make the open editor's next save fail with a conflict, or force the form to reload and lose typing |
| Discard the picture when the note is deleted | The files module cannot un-discard, so a restored note would lose its picture |
| Delete the file in the purge's own transaction | A storage outage would make the purge fail or leave a file nothing refers to |
| A `kibootia.notes.enabled` switch | Goes against ADR 0024 (switches only for infrastructure) and would not show that the code can be deleted |
| Truncate every table in `AdminApiTest` | Needs a list of tables to leave alone (locks, history), and the one test that creates notes can clean up after itself |
| A Spring Data native `Pageable` for the deleted list | Dynamic sorting of native queries is not guaranteed; a whitelist of columns is |
| A second flag in `/me` named for notes | Every feature that keeps files would add another; one flag for the module is enough |

## Consequences

- A feature that keeps a file next to its own data copies `NoteAttachmentService`: declare a category, ask for `StoredFiles` through an `ObjectProvider`, authorize, spend the allowance, store, switch the reference in a short transaction, discard the old file. [The guide](../engineering/guides/add-a-feature.md) walks through it.
- A new removable module is added to `REMOVABLE_MODULES`, to `scripts/remove-module.mjs`, to the matrix and to the recipes; its shared-test stand-in is `WidgetFixtures`.
- The deleted list shows everyone's deleted notes to anyone who may restore them. That is the administrative role by default; a deployment that wants a narrower view grants the permission narrowly.
- Notes are listed with the owner's ID, not the owner's name: the note response carries no profile data, and the screen shows "You" or a short ID. A feature that needs names joins them in its own response.
- The `startup` group is the contract for "the application still starts": a new infrastructure-backed module adds its own off-switch check to `ModuleStartupIT`.
- Local runs of the Maven build must not share `backend/target` with an IDE's Java language server. In this repository the IDE compiled with an older source level and wrote "Unresolved compilation problems" class files into the build, which failed the avatar tests; the milestone's local runs used a copy of `backend/` outside the workspace.

## Status

accepted. Extends [ADR 0024](0024-package-modules-with-infrastructure-toggles.md), [ADR 0029](0029-s3-object-storage-contract.md) and [ADR 0051](0051-milestone-4-implementation-decisions.md), which stay accepted. Resolves OQ-37.

## Related

[Milestone 5](../product/roadmap/milestone-5-example-feature.md), [recipes](../recipes/README.md), [how to add a feature](../engineering/guides/add-a-feature.md), [threat model](../operations/security/threat-model.md), [API conventions](../architecture/api/conventions.md), [architecture enforcement](../architecture/architecture-enforcement.md), [verification standard](../engineering/testing/verification-standard.md).
