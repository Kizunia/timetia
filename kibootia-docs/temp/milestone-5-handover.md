# Milestone 5 handover

Working material, not authoritative: the permanent record is [milestone 5](../product/roadmap/milestone-5-example-feature.md), [ADR 0052](../decisions/0052-milestone-5-implementation-decisions.md) and [implementation status](../product/roadmap/implementation-status.md). Written 2026-11-11 at the end of an unattended session.

## Where things stand

Milestone 5 is **built and observed locally. It is not verified**: nothing was pushed, no pull request exists, and no CI run covers any of it. Everything is on the local branch `m-5` (10 commits on top of `main`). Your `.gitignore` edit and the untracked `.vscode/` were left alone and are in no commit.

## What was built

- **Backend.** A note has one picture, kept whole in a `preview` (480) and a `full` (1600) size (new "fit" rendition in the files pipeline; avatars unchanged). `PUT/DELETE/GET /api/v1/notes/{id}/attachment`. Permission follows the note; the file belongs to the note's owner; the upload allowance is spent after the permission check; attaching does not change the note's version. A deleted note keeps its picture hidden so a restore is complete; the retention purge publishes `NotePurged` and a durable listener discards the file. `GET /api/v1/notes?deleted=true` lists deleted notes for someone who may restore. `/me` has `filesEnabled`.
- **Removability.** Shared tests no longer name notes (`WidgetFixtures`); architecture rule 5 covers every module in `REMOVABLE_MODULES`; `ArchitectureRulesFireTest` shows rules 5, 7, 9, 10 and 11 fail on classes made to break them; a `startup` test group exists for the CI rows.
- **Frontend.** Notes list, editor and picture; `src/modules.ts` as the one list the shell reads; a shared image check; the unit tests of other features no longer use the notes endpoints.
- **Scripts and CI.** `scripts/remove-module.mjs notes`; the `module-removal` job (four rows); `KIBOOTIA_FILES_ENABLED` and `KIBOOTIA_OBSERVABILITY_ENABLED` in `.env.example`.
- **Documents.** ADR 0052, the guide [add a feature](../engineering/guides/add-a-feature.md), three [recipes](../recipes/README.md), and updates to the milestone, status, enforcement, testing, API, configuration, threat model and start-and-check documents. OQ-37 closed (the name stays "Notes").

## What ran, and what did not

| Check | Result |
|---|---|
| Backend, full run of the final tree | 135 fast, 269 integration. One test failed (`NoteAttachmentRateLimitIT`, shared a Spring context and limiter with the avatar one). Fixed; it and `AvatarRateLimitIT` passed together. **A second full run did not complete: Docker Desktop stopped, and I did not start it** (it would have brought your dev containers back) |
| Backend with notes removed by the script | 115 fast, 209 integration, all pass |
| Frontend, final tree | lint clean, type check clean, 196 unit tests, production build passes |
| Frontend with notes removed | client regenerated from the rewritten `openapi.json`, lint, type check and 171 tests pass. **The production build of that checkout was not run** (pnpm and Turbopack refuse a linked `node_modules`, and a real install failed on the long scratchpad path) |
| `startup` group with each switch set through the environment (CI rows 2 to 4) | **Not run.** It passed with the default switches. `FilesDisabledIT` and `NoteAttachmentFilesDisabledIT` cover files-off by property; all integration tests already run with telemetry export off |
| Playwright (31 tests, 9 new) | **Not run.** Written and type-checked; needs the full stack. This is the part most likely to need a fix once CI runs it |
| The workflow file | Parses; the matrix is the four rows. Not run |

## Things I found that you should know

1. **The IDE corrupts Maven builds.** Its Java language server compiles `backend/` with a source level below 21 (it flags the pattern `switch` in the avatar code) and writes "Unresolved compilation problems" class files into `backend/target`. My first full run failed 11 avatar tests because of it. Every run after that used a copy of `backend/` outside the workspace. If your CI or local builds ever show that error, this is the cause. Worth fixing the IDE's Java source level.
2. **Tests that name a module break its removal.** Fourteen test files used `notes.notes.*` or `/api/v1/notes` as convenient stand-ins; they now use core endpoints or `WidgetFixtures`. A new removable module should follow the same rule (the guide says so).
3. **Spring Modulith cannot be shown to fail from test sources** (it ignores test classes). The cycle and access rules are shown non-vacuous by the real module graph instead. Recorded in the ADR.
4. **Removal found real gaps.** The first run of the script on a clean checkout found five leftover mentions; the scan now skips comments and has an allow-list of two intentional ones.

## Departures from the approved plan

- `AdminApiTest` does not clear the `note` table; the one test that creates notes clears it (the plan was to truncate every table).
- `NoteServiceTest` became `NoteAttachmentServiceTest` for the plain logic only: `Authorization` cannot be built or mocked in this codebase without a new test style. The check order is covered through the API and the rate-limit test.
- One column (`attachment_file_id`) instead of two; the file ID is the version.
- Architecture rules are shown to fire in a separate test class instead of in `ArchitectureTest` itself.

## Known limits (also in the milestone document)

One picture per note, images only; notes list the writer as a short ID; attaching or removing a picture is not audited; the removal script is only meaningful for `notes`.

## Stale things I left alone

- The headings of [milestone 6](../product/roadmap/milestone-6-production-readiness.md) still say its criteria are "awaiting owner approval", although OQ-49 records the approval. I fixed the roadmap index and milestone 5 only.
- The CI image scan (part of OQ-44) is still unrun.

## To do next, in order

1. Start Docker Desktop. Run `./mvnw verify` from a copy of `backend/` outside the workspace, and the `startup` group once with `KIBOOTIA_FILES_ENABLED=false`, once with `KIBOOTIA_OBSERVABILITY_ENABLED=false`.
2. Push `m-5`, open a **draft** pull request with the `e2e` label, and merge with `gh pr merge N --rebase` once green. Expect to fix something in `notes.spec.ts`.
3. When CI passes, replace "observed (local run; no CI run yet)" with the run ID in the milestone, status, ADR 0052, the three recipes and the guide, and commit `docs: mark milestone 5 as verified (CI run ...)`.

## Where things are

- Branch `m-5` in `d:\projects\kibootia`. Throwaway material (the isolated build copy, the logs, helper scripts) is in the session scratchpad and can be deleted.
- Plan: `C:\Users\Admin\.claude\plans\aim-complete-the-milestione-binary-heron.md`.
