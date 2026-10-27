# Docs Structure

> Status: **FINAL PROPOSAL, awaiting migration approval.** The decisions below were chosen by the project owner. No existing file has been moved yet; section 11 describes the migration.

The docs folder will hold thousands of Markdown files. This document defines where every kind of document lives, how files are named, and how the tree scales without losing order.

## 1. Decisions taken

| # | Decision | Choice |
|---|---|---|
| 1 | Top-level split | **Hybrid.** Domain folders for modules, plus a small set of platform areas (shared, architecture, engineering, operations, decisions). |
| 2 | Folder naming | **Plain names, no numeric prefixes.** Reading order comes from README indexes. |
| 3 | Module internals | **Required minimum plus optional folders** chosen from a fixed list. |
| 4 | File granularity | **One concern per file, with small related items allowed to be grouped.** Splitting is preferred over combining. |
| 5 | Architecture decisions (ADRs) | **Per module**, plus one **central index file** that references every ADR in the project. |
| 6 | Governance | **Conventions only.** No tooling or CI for now; rules are enforced in review. |
| 7 | Retired docs | **`_archive/` inside each area**, plus a `status` field. |

## 2. Principles

1. **One concern per file, one home per concern.** If a file needs two top-level topics, split it.
2. **Everything about a module lives in that module's folder.** It can be read, reviewed, owned or handed over on its own. This supports the goal of a loosely coupled, independent module.
3. **Anything used by more than one module lives in `shared/`**, defined once and linked everywhere else. Duplicated text is a defect.
4. **Every folder has a `README.md` index** listing its children with a one-line summary each.
5. **Names are stable.** Renaming breaks links, so names describe the topic, never the date, version or author.
6. **Nothing is deleted from the tree silently.** Retired docs move to `_archive/`.

## 3. Top-level layout

```
docs/
├── README.md              # Entry point: map of the docs, how to find things
├── STRUCTURE.md           # This file (moves to meta/ after migration)
├── meta/                  # How we write docs: rules, style, templates
├── product/               # Vision, personas, roadmap, cross-module requirements
├── modules/               # One folder per module (the main body of the docs)
├── shared/                # Cross-module definitions: glossary, roles, entities, enums
├── architecture/          # System-wide design and ERP integration
├── engineering/           # How we build: setup, conventions, testing
├── operations/            # Deploy, configuration, monitoring, runbooks, security
├── decisions/             # Project-wide ADRs and the central ADR index
├── research/              # Investigations and source-material analysis (non-authoritative)
└── guides/                # Task-oriented how-tos by audience
```

`modules/` keeps its current name, so the existing path `docs/modules/` stays valid.

## 4. What belongs in each area

### `meta/`
Rules about the docs themselves. If it is about *how we document*, it goes here.
```
meta/
├── README.md
├── contributing.md          # How to add or change a doc, review expectations
├── style-guide.md           # Tone, headings, tables, diagrams, language
├── naming-conventions.md    # Section 7 of this file, expanded
├── doc-header.md            # The standard header block (section 8)
├── doc-types.md             # Definition of each doc type and where it lives
├── lifecycle.md             # Status values and archiving rules
└── templates/               # One template per doc type (section 9)
```

### `product/`
```
product/
├── README.md
├── vision.md
├── goals-and-non-goals.md
├── personas/                # One file per persona
├── roadmap/                 # One file per milestone or release
└── requirements/            # Cross-module requirements, one file each
```

### `modules/`
One folder per module; see section 5.

### `shared/`
Definitions that more than one module relies on. Each is defined here once.
```
shared/
├── README.md
├── glossary/                # One file per term (see section 6)
├── roles/                   # One file per role (Super Admin, Super HOD, HOD, Faculty, CR/ACR, BR)
├── entities/                # One file per entity (Lecture, Slot, Room, Batch, Division, Ticket)
├── enums/                   # Shared value lists: statuses, change types
└── permissions-matrix.md    # Role by capability overview, linking to role files
```

### `architecture/`
```
architecture/
├── README.md
├── overview.md
├── context-and-boundaries.md      # What the system owns versus the ERP owns
├── erp-integration/               # Contracts, authentication, data sync
├── data/                          # Cross-module data conventions, ERD, migration policy
├── api/                           # API-wide conventions: errors, pagination, versioning
└── diagrams/                      # Diagram sources (e.g. Mermaid) and exports
```

### `engineering/`
```
engineering/
├── README.md
├── getting-started/         # Local setup; environment variables described, never values
├── conventions/             # Code style, branching, commit messages
├── testing/                 # Strategy, test data
└── tooling/
```

### `operations/`
```
operations/
├── README.md
├── deployment/
├── configuration/
├── monitoring/
├── runbooks/                # One file per incident type
└── security/                # Threat model, secrets handling, access policy
```

### `decisions/`
Project-wide ADRs (those not owned by one module) and the central index. See section 10.

### `research/`
Time-bound, non-authoritative. When a finding becomes a rule, it is promoted to the relevant module, `shared/` or an ADR, and the research note links to where it went.
```
research/
├── README.md
├── source-material/         # Analysis of existing timetables, ERP exports, uploads
├── comparisons/
└── spikes/
```

### `guides/`
Task-oriented documents for end users, written from the module specs once those are stable.
```
guides/
├── README.md
├── admin/
├── hod/
├── faculty/
└── students/
```

## 5. Anatomy of a module

### Required (every module has these)

```
modules/<module-name>/
├── README.md              # Summary, status, owner, links to every part of the module
├── overview/              # Problem, goals, scope, out-of-scope
├── requirements/          # Functional and non-functional requirements, one file each
└── open-questions.md      # Live list of unresolved decisions, each with an owner
```

### Optional (add only when the module has content for them)

| Folder | Holds |
|---|---|
| `flows/` | User flows, one file per flow, grouped by role when large |
| `rules/` | Business rules: approval, lifecycle, conflict handling, permissions |
| `data/` | Module data model and field definitions |
| `api/` | Endpoints, one file per resource |
| `ui/` | Screens, wireframes, copy |
| `notifications/` | Triggers and message templates |
| `testing/` | Test scenarios and acceptance criteria |
| `decisions/` | The module's ADRs (section 10) |
| `integration/` | How this module talks to other modules or the ERP |
| `changelog.md` | Human-readable history of spec changes |
| `_archive/` | Retired docs of this module |

Rules:
- Optional folders are created only when they have at least one real file.
- If a folder passes roughly 25 files, split it by sub-concern (e.g. `flows/faculty/`, `flows/hod/`).
- A module never contains another module's files. Shared facts go to `shared/`.

### Example: lecture-rescheduling after migration

```
modules/lecture-rescheduling/
├── README.md
├── open-questions.md
├── changelog.md
├── overview/        problem.md, goals.md, scope.md
├── requirements/    one file per requirement
├── flows/           faculty-reschedule-theory.md, faculty-reschedule-lab.md, exchange.md,
│                    extra-lecture.md, hod.md, cr-br.md, student.md, super-admin.md
├── rules/           ticket-lifecycle.md, approval-model.md, conflict-rules.md, permissions.md
├── data/            room-attributes.md, ticket-fields.md
├── notifications/   triggers.md
├── integration/     erp-ownership.md
└── decisions/       0001-... (decisions already taken in the spec)
```

## 6. Granularity rules

- **Default is one concern per file.** A flow, a rule, a role, an entity, an endpoint, an ADR: one file each.
- **Grouping is allowed** when items are tiny, always read together, and share one lifecycle (for example three short enum values of one enum, or a few closely related fields). Group under a descriptive name, never `misc.md` or `other.md`.
- **Split triggers:** a file over about 400 lines, a file with two unrelated H2 sections, or a section other docs want to link to on its own.
- **Glossary** is one file per term in `shared/glossary/`, named after the term. Once it holds more than about 100 terms, it is split into letter sub-folders (`a/`, `b/`) and the README keeps the alphabetical index.

## 7. Naming conventions

- **Files and folders:** lowercase kebab-case, ASCII, English (`conflict-rules.md`).
- **Index files:** always `README.md`.
- **No numeric prefixes** on folders. Order is expressed in the README index, not in names.
- **ADRs:** `NNNN-short-title.md`, four-digit sequence per scope (see section 10).
- **Templates:** `meta/templates/<doc-type>.md`.
- **Assets** (images, PDFs, diagram exports): an `assets/` folder beside the doc that uses them. No global asset dump.
- **Diagram sources:** `<topic>.mmd` next to the doc, exports in the sibling `assets/`.
- **Names describe the topic**, not the date, version, author or status. Use the header fields for those.
- **Links:** relative paths only; never link to a branch name.

## 8. Standard doc header

Because there is no tooling, the header is a plain block at the top of each doc, readable by humans and easy to automate later:

```markdown
# Title

- **Type:** spec | flow | rules | data | api | adr | runbook | guide | research | reference
- **Module:** lecture-rescheduling (or `shared`, `platform`)
- **Status:** draft | review | accepted | deprecated | archived
- **Owner:** github-login
- **Last updated:** YYYY-MM-DD
```

## 9. Templates

Kept in `meta/templates/`, one per type: `module-readme`, `spec`, `requirement`, `user-flow`, `rules`, `data-entity`, `api-endpoint`, `notification`, `adr`, `runbook`, `research-note`, `glossary-term`, `guide`. New docs start by copying a template.

## 10. Decisions (ADRs)

- **Module decisions** live in `modules/<module>/decisions/`, numbered per module (`0001-...`).
- **Project-wide decisions** (affecting more than one module) live in `decisions/`, numbered separately.
- **Central index:** `decisions/INDEX.md` lists every ADR in the project in one table: ID, title, scope (module or `platform`), status, date, link. Whoever adds or changes an ADR must update the index in the same PR.
- ADR sections: Context, Decision, Alternatives considered, Consequences, Status.
- An accepted ADR is never rewritten. A change is a new ADR that supersedes the old one, and both statuses are updated in the index.

## 11. Lifecycle and archiving

- Status values: `draft` → `review` → `accepted` → `deprecated` → `archived`.
- When a doc is retired, it moves to the `_archive/` folder of its area (e.g. `modules/lecture-rescheduling/_archive/`), its status becomes `archived`, and a line at the top says what replaced it.
- Archived docs are not deleted, so old links can be repaired rather than lost.

## 12. Governance (conventions only)

No tooling for now. Rules are enforced in PR review:
- Every PR that adds a doc uses the right template, header, folder and name.
- Every new or renamed file is added to its folder's `README.md`.
- ADR changes update `decisions/INDEX.md`.
- Facts defined in `shared/` are linked, never copied.
- Recommended later, not now: CODEOWNERS per area, link checking, markdown linting, generated indexes, a docs site. The structure is designed so these can be added without moving files.

## 13. Migration plan (needs your approval first)

1. Create the skeleton folders with `README.md` stubs and `meta/templates/`.
2. Split `modules/lecture-rescheduling.md` into the module folder shown in section 5. Mapping:

| Current section | New home |
|---|---|
| 1 Problem, 2 Goal, 4 Scope | `overview/problem.md`, `goals.md`, `scope.md` |
| 3 Glossary | one file per term in `shared/glossary/` |
| 5 Ownership and ERP integration | `integration/erp-ownership.md`, boundaries summarized in `architecture/context-and-boundaries.md` |
| 6 Roles and permissions | roles to `shared/roles/`, module-specific powers to `rules/permissions.md` |
| 7 Change types | `shared/enums/change-types.md` |
| 8 User flows (8.1 to 8.8) | `flows/`, one file per flow |
| 9 Ticket lifecycle and approval | `rules/ticket-lifecycle.md`, `rules/approval-model.md` |
| 10 Conflict rules | `rules/conflict-rules.md` |
| 11 Notifications | `notifications/triggers.md` |
| 12 Data the module needs, room attributes | `data/` and `shared/entities/room.md` |
| 13 Open questions | `open-questions.md` |
| 13 Decisions taken | ADRs in `decisions/`, listed in `decisions/INDEX.md` |

3. Replace the original file with the module `README.md` linking to all parts.
4. Move this file to `meta/structure.md` and write `docs/README.md`.

Each step is a separate commit so it can be reviewed or reverted independently.

## 14. Housekeeping note

The repository currently contains a committed `.env` with database credentials. It should be removed from version control, added to `.gitignore`, and the credentials rotated. This is outside the docs structure but is recorded here so it is not lost.
