# Kibootia Documentation

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (index).

Kibootia is a reusable Spring Boot and Next.js application starter. These docs describe what it is intended to be, which decisions have been taken, and what currently exists in the repository. They are kept in step with the code (see [living documentation](meta/living-documentation.md)).

The documentation conventions (areas, naming, headers, status values, ADRs, links and portability across forks) are defined in [documentation standards](meta/documentation-standards.md). Earlier versions relied on a separate `STRUCTURE.md`; that file was removed, see [ADR 0022](decisions/0022-maintain-own-documentation-standards.md).

## How to read the status of a statement

Every document says whether it describes a decision, a requirement, a plan, observed code or verified behavior. The vocabulary is defined in [living documentation](meta/living-documentation.md). In short: a document being `accepted` or an ADR being accepted never means the behavior exists. Check [implementation status](product/roadmap/implementation-status.md) for that.

## Map

| Area | What it holds |
|---|---|
| [meta/](meta/README.md) | [Documentation standards](meta/documentation-standards.md), how docs are kept current, plus templates |
| [product/](product/README.md) | Vision, goals and non-goals, milestones, implementation status |
| [shared/](shared/README.md) | Glossary of terms used across documents |
| [architecture/](architecture/README.md) | System design: boundaries, identity, data, API, communication |
| [engineering/](engineering/README.md) | Local setup, conventions, testing and verification |
| [operations/](operations/README.md) | Configuration, deployment, monitoring, secrets |
| [decisions/](decisions/README.md) | Architecture decision records, the [central ADR index](decisions/INDEX.md) and [open questions](decisions/open-questions.md) |

## Where to start

- New to the project: [vision](product/vision.md), then [architecture overview](architecture/overview.md).
- Wondering what works today: [implementation status](product/roadmap/implementation-status.md).
- Why something is the way it is: [ADR index](decisions/INDEX.md).
- What is still undecided: [open questions](decisions/open-questions.md).

## Non-authoritative material

[`temp/kibootia-project-definition.md`](temp/kibootia-project-definition.md) is the source document the first version of these docs was derived from. It is not part of the permanent set and is not kept in step with changes. When it and a document here disagree, the document here, together with the ADRs, wins.
