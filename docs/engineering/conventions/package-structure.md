# Package Structure

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. The repository has the single package `com.kibootia.backend` containing `BackendApplication`.

## Purpose

Hold the package rules once they exist. At present only the constraints are decided; the layout is not.

## Decided constraints (from the architecture decisions)

- One Maven module for the backend. Separate Maven modules per feature are not an initial requirement.
- Packages express [features](../../shared/glossary/feature.md) and [shared platform capability](../../shared/glossary/platform-package.md), not technical layers across the whole application.
- Shared packages stay narrow; no catch-all utility package.
- Package names must make the rules in [architecture enforcement](../../architecture/architecture-enforcement.md) expressible as ArchUnit tests.
- The layering in [layers and boundaries](../../architecture/layers-and-boundaries.md) applies inside each feature.

## Not decided

- The names of the top-level packages and how a feature separates its public contract from its internals.
- Where ports and adapters live inside a feature.
- Whether the starter ships an example feature. The owner chose not to create `docs/modules/` yet.

These block [milestone 1](../../product/roadmap/milestone-1-architectural-skeleton.md) and are tracked in [open questions](../../decisions/open-questions.md).
