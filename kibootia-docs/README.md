# Kibootia Documentation

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

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
| [technology/](technology/README.md) | Technology catalogue: tier, selection and reasoning, including technologies not selected |
| [engineering/](engineering/README.md) | Local setup, conventions, testing and verification |
| [operations/](operations/README.md) | Configuration, deployment, monitoring, secrets, threat model |
| [decisions/](decisions/README.md) | Architecture decision records, the [central ADR index](decisions/INDEX.md) and [open questions](decisions/open-questions.md) |

## Where to start

- New to the project: [vision](product/vision.md), then [architecture overview](architecture/overview.md).
- Wondering what works today: [implementation status](product/roadmap/implementation-status.md).
- Why something is the way it is: [ADR index](decisions/INDEX.md).
- What is still undecided: [open questions](decisions/open-questions.md).
- Whether implementation can start: [implementation readiness review](temp/kibootia-implementation-readiness.md).

## Non-authoritative material

`temp/` holds working material: [`kibootia-project-definition.md`](temp/kibootia-project-definition.md) is the source of the first version of these docs (marked historical), [`kibootia-ideation-decisions.md`](temp/kibootia-ideation-decisions.md) is the brief that started the 2026-10-28 interview, [`kibootia-decision-log.md`](temp/kibootia-decision-log.md) records the decisions of both interview rounds, and [`kibootia-implementation-readiness.md`](temp/kibootia-implementation-readiness.md) assesses whether implementation planning can start. None is part of the permanent set or kept in step with changes. When one of them and a document here disagree, the document here, together with the ADRs, wins. The project definition predates those decisions; its exclusions of object storage, a full observability stack and user administration no longer apply.
