# Living Documentation

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (process rules).

Documentation is part of engineering work in Kibootia. This document defines how it stays accurate. Folder, naming, header and ADR rules are in [STRUCTURE.md](../STRUCTURE.md).

## Source-of-truth order

1. Current decisions of the project owner.
2. Accepted [ADRs](../decisions/INDEX.md).
3. The permanent docs in this tree.
4. Current code and automated tests, as evidence of what works.
5. Drafts, proposals and notes (including `docs/temp/`).

This order says which source wins in a conflict. It never allows an intended design to be described as implemented.

## Implementation-status vocabulary

The standard header has a `Status` field (draft, review, accepted, deprecated, archived) that describes the document. It says nothing about the code. So each document carries one extra line directly under the header:

```markdown
> **Implementation:** planned | observed | verified | not applicable
```

| Value | Meaning |
|---|---|
| planned | Decided or proposed, but not present in the repository |
| observed | Present in the repository, but its behavior has not been shown to work by passing tests |
| verified | Covered by automated tests that were run and passed in CI (see [verification standard](../engineering/testing/verification-standard.md)) |
| not applicable | The document does not describe behavior (index, process, decision text) |

Documents may qualify the line, for example "planned; pom.xml currently still says Java 17". The per-capability picture is kept in one place: [implementation status](../product/roadmap/implementation-status.md).

## Kinds of statements

Documents keep these apart, in wording or in labelled sections:

- **Owner decisions** (usually recorded as an ADR).
- **Observations** from the repository, with the file named.
- **Inferences** drawn by the author.
- **Recommendations** not yet accepted.
- **Open questions**, linked to [open questions](../decisions/open-questions.md).

Provisional decisions are recorded with `Status: draft` and listed in the open questions.

## When code changes, check these

Architecture, package boundaries, module contracts, API behavior, authentication or authorization, schema or persistence, configuration or environment variables, deployment, testing, operations, dependencies and user-visible behavior. Update the affected documents in the same commit or pull request as the code. Also update:

- the README of any folder where a document is added, renamed, moved or retired;
- [implementation status](../product/roadmap/implementation-status.md);
- [INDEX.md](../decisions/INDEX.md) whenever an ADR is added or changes status;
- links broken by a move or rename.

Do not rewrite unrelated documents. Keep a fact in one place and link to it.

## When code and documents disagree

1. Name the exact disagreement.
2. Decide whether the code is incomplete, the document is stale, or the intent is unclear.
3. If intent cannot be established, ask the owner and record it in [open questions](../decisions/open-questions.md).
4. Update the right source of truth, then re-check links.

Code proves what is implemented, not what was intended.

## ADRs

Accepted ADRs are never rewritten. A changed decision is a new ADR that supersedes the old one; both statuses and the index are updated together.
