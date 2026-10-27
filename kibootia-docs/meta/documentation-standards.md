# Documentation Standards

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (process rules).

This is the authority for how Kibootia's documentation is organized, named, headed, indexed and retired. It replaces the former `STRUCTURE.md` (see [ADR 0022](../decisions/0022-maintain-own-documentation-standards.md)). How docs stay accurate as code changes, and the implementation-status vocabulary, are in [living documentation](living-documentation.md); this document links to that one rather than repeating it.

The rules describe the tree as it exists. They are written to be forked: a downstream project can keep them, trim them or supersede them with its own ADR (see [section 10](#10-portability-across-forks)).

## 1. Principles

1. **One canonical home per fact.** State a fact once, in the document that owns it, and link to it from everywhere else. Duplicated text is a defect. Definitions used by more than one document go in [shared/](../shared/README.md).
2. **Focused documents.** One concern per file. Split a file when it covers two unrelated topics, when it passes about 400 lines, or when another document wants to link to one of its sections on its own. Small items that are always read together and share a lifecycle may be grouped under a descriptive name, never `misc.md` or `other.md`.
3. **A clear source of truth.** The precedence order is defined in [living documentation](living-documentation.md#source-of-truth-order). A document must not claim more authority than its place in that order gives it.
4. **Names are stable.** Renaming breaks links, so names describe the topic, never a date, version, author or status. Those belong in the header.
5. **Nothing is silently dropped.** Retiring a document follows [section 11](#11-obsolete-and-superseded-documentation).

## 2. Documentation areas

| Area | Responsibility | Not for |
|---|---|---|
| [meta/](README.md) | How the docs are written and kept current: these standards, living documentation, [templates](templates/README.md) | Facts about the product or code |
| [product/](../product/README.md) | Vision, goals and non-goals, milestones, [implementation status](../product/roadmap/implementation-status.md) | Design detail |
| [shared/](../shared/README.md) | Definitions used by more than one document; today the [glossary](../shared/glossary/README.md) | Anything used by one document only |
| [architecture/](../architecture/README.md) | System design: boundaries, layers, identity, data, API, communication, enforcement | Setup steps, rationale for a single choice |
| [engineering/](../engineering/README.md) | How the project is built: local setup, code conventions, testing and verification | Runtime deployment |
| [operations/](../operations/README.md) | Configuration, deployment, monitoring, secrets | Application design |
| [decisions/](../decisions/README.md) | ADRs, the [central index](../decisions/INDEX.md), [open questions](../decisions/open-questions.md) | Descriptions of current behavior |
| `temp/` | Source or scratch material the permanent docs were derived from. Non-authoritative and not kept in step with changes | Anything other documents rely on |

Rules for areas:

- The set of areas above is what exists today. Add an area or sub-folder only when it has at least one real document; do not create empty placeholders. Sub-folders in use (for example `architecture/identity/`, `operations/security/`) are legitimate wherever they group several documents.
- A document describing something belongs in the area that owns that concern. The reason for a choice belongs in an ADR, and the description of the resulting design links to it.
- The glossary is one file per term, named after the term. Split it into letter sub-folders when it passes about 100 terms.

## 3. Naming

- **Files and folders:** lowercase kebab-case, ASCII, English (`token-validation.md`).
- **No numeric prefixes** on folders or ordinary files. Reading order is expressed in the folder's `README.md`. Roadmap files named `milestone-N-...` are the one exception: the number is part of the milestone's identity.
- **Index files** are always `README.md`.
- **ADRs:** `NNNN-short-title.md` (see [section 7](#7-architecture-decision-records)).
- **Templates:** `meta/templates/<doc-type>.md`.
- **Assets** (images, diagram exports): an `assets/` folder beside the document that uses them. No global asset dump. Prefer Mermaid in the Markdown itself where it is enough.

## 4. Indexing

- Every folder has a `README.md` listing its children, one line each. The root `README.md` is the map of the whole tree.
- Whoever adds, renames, moves or retires a document updates that folder's `README.md` in the same change.
- `decisions/` additionally has [INDEX.md](../decisions/INDEX.md), the table of every ADR.
- An index is a navigation aid, not a place for content. If an index grows prose that is not about navigation, move the prose to a focused document.

## 5. Header and status

Every document starts with:

```markdown
# Title

- **Type:** spec | rules | reference | adr | guide | api
- **Module:** platform
- **Status:** draft | review | accepted | deprecated | archived
- **Owner:** github-login
- **Last updated:** YYYY-MM-DD

> **Implementation:** planned | observed | verified | not applicable
```

Field notes:

- **Type** values are those in use. A new type (for example `runbook`) is added here, with a template, the first time it is needed.
- **Module** is `platform` for project-wide documents and `shared` for glossary-style definitions. A project that documents separate feature modules may use a module name here; Kibootia itself defines none.
- **Last updated** changes when the meaning of the document changes, not for typo fixes.
- The `Implementation` line sits directly under the header and may carry a qualification after the value.

**Document status** says how far the *document* has been agreed:

| Status | Meaning |
|---|---|
| draft | Written but not yet confirmed by the owner; may contain provisional choices |
| review | Submitted for confirmation |
| accepted | Confirmed by the owner as the current statement |
| deprecated | No longer current; replaced or no longer applies. Says what replaced it |
| archived | Kept only for history, outside the live set (see [section 11](#11-obsolete-and-superseded-documentation)) |

Typical flow: `draft` → `review` → `accepted` → `deprecated` → `archived`. Provisional decisions stay `draft` and are listed in [open questions](../decisions/open-questions.md).

## 6. Document status versus implementation status

These are independent and must never be conflated.

- **Document status** (`Status:`) is about the text.
- **Implementation status** (`Implementation:`) is about the repository. Its values and meaning are defined in [living documentation](living-documentation.md#implementation-status-vocabulary), and the per-capability picture is kept only in [implementation status](../product/roadmap/implementation-status.md).

An `accepted` document or ADR can have `Implementation: planned`. Acceptance records agreement; it is not evidence that anything exists.

### Describing planned, observed and verified behavior

- **Planned:** use intent wording ("will", "is intended to", "the decision is"). Never use present tense for something not in the repository.
- **Observed:** state what is present and name the file or path. Say that its behavior is unproven unless a passing test shows otherwise.
- **Verified:** only when automated tests covering the behavior were run and passed, per the [verification standard](../engineering/testing/verification-standard.md). Name the test or pipeline run. Reading code or running the app by hand does not make something verified.
- When a snapshot was taken without running a build or tests, say so, as [implementation status](../product/roadmap/implementation-status.md) does.
- Keep owner decisions, observations, inferences, recommendations and open questions distinguishable, as described in [living documentation](living-documentation.md#kinds-of-statements).
- Never raise a level to make a document look finished. When unsure, use the lower level.

## 7. Architecture Decision Records

ADRs live in [decisions/](../decisions/README.md). They record a decision and its reasoning at a point in time; they do not describe the current state of the code.

- **Name:** `NNNN-short-title.md`. `NNNN` is a four-digit sequence, one number per ADR, in order of creation, never reused or reassigned. The next ADR takes the next unused number.
- **Structure:** copy the [ADR template](templates/adr.md). Sections are Context, Decision, Alternatives considered, Consequences, Status, Related. The standard header applies, with `Type: adr`.
- **Index:** add the ADR to [INDEX.md](../decisions/INDEX.md) (ID, title, scope, status, date, link) and to the count in the [decisions README](../decisions/README.md) in the same change. Index status must match the ADR's own status.
- **Status:** a decision that is chosen but still provisional is `draft` and appears in open questions. `accepted` means the owner confirmed it. Accepted does not mean implemented.
- **Accepted ADRs are not rewritten.** Their Context, Decision, Alternatives and Consequences stay as written, even when later wrong. Only these edits are allowed: status and `Last updated`, a note pointing to the replacing ADR, link repairs, and typo fixes.
- **Changing a decision:** write a new ADR that says what it supersedes and why. In the old ADR, set `Status` to `deprecated`, add a short note under the header and in the Status section naming the replacing ADR, and update the index row (`deprecated (superseded by NNNN)`). If only part of an old ADR is superseded, say which part in both places; the old ADR is `deprecated` only when its decision as a whole no longer holds. Otherwise leave it `accepted` and add the note.
- **Open questions:** when an ADR resolves an entry in [open questions](../decisions/open-questions.md), remove the entry and note the resolution in the ADR.
- **Scope column:** `platform` for all current ADRs. A module name is used only if a project defines module-scoped ADRs.

## 8. Maintenance when code or decisions change

The checklist of what to re-check when code changes, and how to handle code and documents that disagree, is in [living documentation](living-documentation.md#when-code-changes-check-these). In addition:

- A new document: use the template for its type if one exists, add the header, and add it to its folder's `README.md`.
- A moved or renamed document: fix every inbound link, update both folders' `README.md`, and search the tree for the old name before finishing.
- A decision change: follow [section 7](#7-architecture-decision-records). Then update the architecture or operations documents that described the old decision, so no live document describes a superseded choice as current.
- Changes to these standards are themselves documented: a change that alters a rule others rely on gets an ADR; a clarification does not.
- Enforcement is by review. No tooling is required. Link checking, Markdown linting or generated indexes may be added later without moving files.

## 9. Links

- Link between documents with **relative paths** only, for example `../decisions/INDEX.md`. Never use absolute paths, host URLs of the repository, or branch names.
- **Never include the name of the documentation root** (`kibootia-docs/` or a fork's equivalent) in a link or in prose that acts as a path. Refer to locations relative to the root, such as `temp/` or `decisions/`.
- Link to the heading or document that owns a fact rather than restating it.
- External links point at stable pages and say what they are.
- When a link target is only planned, do not link: name it in plain text as planned, or record it in open questions.

## 10. Portability across forks

Kibootia is meant to be forked. A downstream project may rename the docs folder (for example to `timetia-docs/`), add its own documents and decisions, and diverge from the starter's product choices.

- **Rename-safe:** because links are relative and never contain the root's name, renaming the root requires no edits inside the tree. This is a rule for every document in it (see [section 9](#9-links)).
- **Inheriting ADRs:** the starter's ADRs record Kibootia's choices, not requirements on a fork. A fork that decides differently writes its own ADR that supersedes the starter's, following [section 7](#7-architecture-decision-records). It does not edit the inherited ADR's decision.
- **Numbering:** a fork continues from the highest existing number. If merging starter updates later produces a number collision, the fork renumbers its own ADRs and fixes its links.
- **Product documents:** `product/`, the glossary, and the architecture and operations specs describe the starter. A fork replaces or extends them for its own product. Nothing in these standards requires a fork to keep Kibootia's vision, milestones or terminology.
- **Adapting these standards:** a fork may add areas (for example a documentation folder per feature module, user guides, or runbooks), add document types and templates, or tighten rules. It records a change of authority as an ADR, as Kibootia did in [ADR 0022](../decisions/0022-maintain-own-documentation-standards.md). Rules that are specific to a fork's product belong in that fork's own documents, not in this file.

## 11. Obsolete and superseded documentation

- **Superseded ADRs** stay in `decisions/` with status `deprecated` and a pointer to the replacement. They are not moved or deleted, so history and links stay intact.
- **Other documents that no longer apply** first become `deprecated`, with a line under the header saying what replaced them. Then choose:
  - If other documents or ADRs still link to it for history, move it to an `_archive/` folder inside its area, set `Status: archived`, and keep the pointer line. Create `_archive/` only when the first such document exists.
  - If nothing depends on it and its history is in version control, delete it, remove it from the folder `README.md`, and repair or remove every link.
- Before either action, search the tree for references to the document and classify each as live (fix it) or intentional history (leave it, in an ADR or an archived document).
- Never leave a live document pointing at a deprecated or deleted one as if it were current. Never move obsolete content elsewhere in the live tree just to keep it.

## 12. Scope and exceptions

- These standards require only what a document needs. They do not require every area to be populated, every type of document to exist, or a feature-module folder to be created before a feature exists. The [feature](../shared/glossary/feature.md) glossary entry notes a reserved name `modules/` for documenting feature modules once they exist; creating it is optional until then.
- Create a folder only for at least one real document. Split a folder by sub-concern when it passes roughly 25 files.
- A document may deviate from a rule when the reason is recorded in that document (or in an ADR when the deviation is a convention others should follow). Deviations are visible and few: the platform-wide [open questions](../decisions/open-questions.md) file, `architecture/identity/` as a subfolder, and the `Implementation:` line were introduced for Kibootia in [ADR 0001](../decisions/0001-adopt-structure-conventions-selectively.md) and are now simply part of these standards.
- Rules in these standards that no one needs yet (for example archiving) apply only when the situation arises.
