# Docs Structure Plan

> Status: **PROPOSAL for review.** Nothing has been moved or renamed yet. Once approved, the migration in section 9 is a single mechanical commit.

The docs folder will eventually hold thousands of Markdown files. This plan keeps every concern in its own place, so that anyone can answer "where does this go?" and "where do I find it?" without searching.

## 1. Design principles

1. **One concern per file, one home per concern.** A file answers one question (what the product does, why a decision was made, how an endpoint behaves). If a file needs two top-level topics, split it.
2. **Separate by kind of document first, then by module.** "What we want" (product), "how it is built" (engineering), and "why we chose" (decisions) change at different speeds and for different readers. They never share a folder.
3. **Module folders are self-contained.** Everything about one module (spec, flows, data, API, tests) lives under that module's folder, so a module can be read, reviewed or handed over on its own. This matches the project goal of a loosely coupled, independent module.
4. **Single source of truth.** Facts live in exactly one file; everything else links to it. Glossary terms, roles and room attributes are defined once in `shared/`.
5. **Every folder has a `README.md` index.** Folders never exceed about 25 entries; when they do, they get sub-folders.
6. **Predictable names.** Lowercase kebab-case, no spaces, no dates in names (dates go in front matter), stable names (links must not rot).
7. **Docs are reviewed like code.** Changes go through PRs; a CODEOWNERS file per top-level area is recommended.

## 2. Top-level layout

```
docs/
├── README.md                  # Entry point: what the docs are, how to navigate, link to this plan
├── STRUCTURE.md               # This file (later moves to meta/structure.md)
│
├── 00-meta/                   # Rules about the docs themselves
├── 10-product/                # What and why, in user terms
├── 20-modules/                # One folder per module (the main body of the docs)
├── 30-shared/                 # Cross-module definitions: glossary, roles, entities
├── 40-architecture/           # System-wide design and integration with the ERP
├── 50-engineering/            # How we build: setup, conventions, testing
├── 60-operations/             # Deploy, config, monitoring, runbooks, security
├── 70-decisions/              # ADRs (architecture decision records)
├── 80-research/               # Investigations, comparisons, source material analysis
├── 90-guides/                 # Audience-specific how-tos (admins, faculty, students)
└── _templates/                # Copy-paste templates for every doc type
```

Numeric prefixes keep a stable, intentional order in file browsers and make the top level scannable. They apply **only to the top level**; below that, names are plain kebab-case.

## 3. What belongs in each folder

### `00-meta/`
Rules for writing docs: `contributing.md`, `style-guide.md`, `naming-conventions.md`, `front-matter.md`, `review-process.md`, `glossary-of-doc-types.md`. If it is about *how we document*, it is here.

### `10-product/`
Product-level material that spans modules.
- `vision.md`, `goals-and-non-goals.md`
- `personas/` (one file per persona: faculty, HOD, CR, student)
- `roadmap/` (one file per release or milestone)
- `requirements/` (cross-module requirements, one file each)

### `20-modules/`
The heart of the docs. One folder per module (see section 4 for internal layout). Initial modules:
- `lecture-rescheduling/`
- Later, for example: `timetable/`, `notifications/`, `rooms/`, `users-and-roles/`, `erp-integration/`

### `30-shared/`
Definitions used by more than one module, each defined once.
- `glossary/` (one file per term, or alphabetical files `a.md`, `b.md` once it grows)
- `roles/` (Super Admin, Super HOD, HOD, Faculty, CR/ACR, BR; one file per role)
- `entities/` (Lecture, Slot, Room, Batch, Division, Ticket; one file per entity)
- `permissions-matrix.md`
- `enums/` (statuses, change types)

### `40-architecture/`
- `overview.md`, `context-and-boundaries.md`
- `erp-integration/` (contracts, auth, data sync)
- `data/` (ERD, naming, migration policy)
- `api/` (API conventions: errors, pagination, versioning)
- `diagrams/` (source files, e.g. Mermaid, plus exported images)

### `50-engineering/`
- `getting-started/` (local setup, env vars described without secrets)
- `conventions/` (code style, branching, commits)
- `testing/` (strategy, test data)
- `tooling/`

### `60-operations/`
- `deployment/`, `configuration/`, `monitoring/`
- `runbooks/` (one file per incident type)
- `security/` (threat model, secrets handling, access policy)

### `70-decisions/`
ADRs, one decision per file: `0001-use-pr-style-ticket-flow.md`. See section 6.

### `80-research/`
Time-bound investigations and analysis. Not authoritative; findings that become decisions are promoted to `70-decisions/` or the relevant module.
- `source-material/` (analysis of the existing timetable PDFs, ERP exports)
- `comparisons/`, `spikes/`

### `90-guides/`
Task-oriented, audience-specific: `admin/`, `faculty/`, `hod/`, `students/`. These explain *how to use* the system and are written last, from the module specs.

### `_templates/`
`module-readme.md`, `spec.md`, `user-flow.md`, `adr.md`, `api-endpoint.md`, `runbook.md`, `research-note.md`, `glossary-term.md`.

## 4. Anatomy of a module folder

Every module uses the **same skeleton**, so readers always know where to look. Folders are created only when they have content.

```
20-modules/<module-name>/
├── README.md              # Index: summary, status, owner, links to everything below
├── 01-overview/           # Problem, goals, scope, out-of-scope, glossary links
├── 02-requirements/       # Functional and non-functional requirements, one file each
├── 03-flows/              # User flows, one file per flow, grouped by role if large
├── 04-rules/              # Business rules: approvals, conflicts, permissions
├── 05-data/               # Module data model, field definitions
├── 06-api/                # Endpoints, one file per resource
├── 07-ui/                 # Screens, wireframes, copy
├── 08-notifications/      # Triggers and message templates
├── 09-testing/            # Test scenarios and acceptance criteria
├── open-questions.md      # Live list of unresolved decisions (each links to an owner)
└── changelog.md           # Human-readable history of spec changes
```

Within a module, the numeric prefixes are optional and used only when reading order matters. If a folder grows past about 25 files, split by sub-concern (for example `03-flows/theory/`, `03-flows/lab/`).

## 5. Where the existing file goes

`docs/modules/lecture-rescheduling.md` is a single 16 KB spec with 13 numbered sections. It should be **split by concern** into the module skeleton above. Proposed mapping (not performed yet):

| Current section | New home |
|---|---|
| 1 Problem, 2 Goal, 4 Scope | `20-modules/lecture-rescheduling/01-overview/problem.md`, `goals.md`, `scope.md` |
| 3 Glossary | terms move to `30-shared/glossary/` (one definition each) |
| 5 Ownership and ERP integration | `40-architecture/erp-integration/` plus a short link from the module README |
| 6 Roles and permissions | roles to `30-shared/roles/`, module-specific powers to `04-rules/permissions.md` |
| 7 Change types | `30-shared/enums/change-types.md` |
| 8 User flows (8.1 to 8.8) | `03-flows/` one file per flow: `faculty-reschedule-theory.md`, `faculty-reschedule-lab.md`, `exchange.md`, `extra-lecture.md`, `hod.md`, `cr-br.md`, `student.md`, `super-admin.md` |
| 9 Ticket lifecycle and approval | `04-rules/ticket-lifecycle.md`, `04-rules/approval-model.md` |
| 10 Conflict rules | `04-rules/conflict-rules.md` |
| 11 Notifications | `08-notifications/triggers.md` |
| 12 Data needed, room attributes | `05-data/` and `30-shared/entities/room.md` |
| 13 Open questions, decisions taken | `open-questions.md`; decisions taken become ADRs in `70-decisions/` |

The original file is then replaced by the module `README.md`, which links to all parts. Old links break once, in that one commit, so the migration should happen before other docs link to it.

## 6. Decisions and ADRs

- One decision per file in `70-decisions/`, numbered sequentially: `NNNN-short-title.md`.
- Sections: Context, Decision, Alternatives considered, Consequences, Status (`proposed`, `accepted`, `superseded by NNNN`).
- ADRs are never edited after acceptance except for status; changes get a new ADR that supersedes the old one.
- Module-specific decisions still live here (tagged with the module in front matter), so there is exactly one decision log.

## 7. Naming conventions

- **Files and folders:** lowercase kebab-case (`conflict-rules.md`). English only, ASCII only.
- **Index file:** always `README.md` (renders automatically on GitHub).
- **Numbered series:** zero-padded (`0001-...`) for ADRs and ordered folders.
- **One topic in the name:** if the name needs "and", split the file.
- **Diagrams:** `<topic>.mmd` (Mermaid source) next to the doc that uses it, exports in a sibling `assets/` folder.
- **Assets:** images and PDFs live in an `assets/` folder beside the doc that references them, never in a global dump.
- **Links:** relative links only; never link to a branch name.

## 8. Metadata and scaling mechanisms

**Front matter** on every doc (small and mandatory):

```yaml
---
title: Conflict rules
type: rules            # spec | flow | rules | data | api | adr | runbook | guide | research | reference
module: lecture-rescheduling   # or "shared"
status: draft          # draft | review | accepted | deprecated
owner: <github-login>
updated: 2026-10-27
---
```

**Scaling mechanisms**
1. **README index in every folder**, listing children with a one-line summary. Indexes are generated by a small script from front matter, not maintained by hand (tooling to be added later, after you approve).
2. **Link checking and front-matter linting** in CI (e.g. markdownlint plus a link checker) so thousands of files stay consistent.
3. **Size limits:** a file over about 400 lines is a candidate for splitting; a folder over about 25 entries gets sub-folders.
4. **Status lifecycle:** deprecated docs move to an `_archive/` folder inside their area rather than being deleted, keeping history reachable.
5. **Cross-references by path, not by copy.** Duplicate text is a defect.
6. **Search:** with consistent front matter, a static site (e.g. MkDocs or Docusaurus) can be added later without restructuring.
7. **Ownership:** a `CODEOWNERS` entry per top-level area so reviews are routed automatically.

## 9. Migration plan (after approval)

1. Create the skeleton folders with `README.md` stubs and `_templates/`.
2. Split `lecture-rescheduling.md` per section 5, adding front matter.
3. Move `docs/STRUCTURE.md` to `00-meta/structure.md` and write `docs/README.md`.
4. Add ADRs for decisions already taken in the spec.
5. Later: add index generator and CI lint.

Each step is its own small commit so it can be reviewed and reverted independently.

## 10. Open decisions for you

1. **Numeric prefixes on top-level folders** (`20-modules/`) versus plain names (`modules/`). Prefixes give a fixed reading order but make links a little longer. The existing file already uses `docs/modules/`, so plain names would avoid one rename.
2. **Glossary granularity:** one file per term (best for thousands of docs, many tiny files) versus one file per letter.
3. **Split depth for the existing spec:** the full split in section 5, or a lighter split (overview, flows, rules, data) first.
4. **Language and audience:** English only, and whether `90-guides/` is needed now or later.

## 11. Housekeeping note

The repository on this branch contains a committed `.env` with database credentials. It should be removed from version control, added to `.gitignore`, and the credentials rotated. This is outside the docs structure but is flagged here so it is not lost.
