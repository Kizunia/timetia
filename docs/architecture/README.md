# Architecture

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (index). Almost everything described below is planned; see [implementation status](../product/roadmap/implementation-status.md).

| Item | Summary |
|---|---|
| [overview.md](overview.md) | System context and the main building blocks |
| [context-and-boundaries.md](context-and-boundaries.md) | What Kibootia owns versus what a consuming application and external systems own |
| [layers-and-boundaries.md](layers-and-boundaries.md) | Layers, model separation, features and shared platform packages |
| [architecture-enforcement.md](architecture-enforcement.md) | Candidate ArchUnit rules |
| [communication.md](communication.md) | Synchronous contracts, in-process events, future messaging |
| [identity/](identity/README.md) | Authentication, token validation, authorization |
| [data/](data/README.md) | Persistence stack, model separation, migrations, transactions, data ownership |
| [api/](api/README.md) | API-wide conventions |

Decisions behind these documents are in the [ADR index](../decisions/INDEX.md). Diagrams are inline Mermaid.
