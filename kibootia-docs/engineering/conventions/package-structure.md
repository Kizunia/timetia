# Package Structure

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. The repository has the single package `com.kibootia.backend` containing `BackendApplication`.

## Purpose

Hold the package rules once they exist. At present only the constraints are decided; the layout is not.

## Decided constraints (from the architecture decisions)

- One Maven module for the backend. Separate Maven modules per feature are not an initial requirement ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)).
- Packages express [features](../../shared/glossary/feature.md) and [shared platform capability](../../shared/glossary/platform-package.md), not technical layers across the whole application.
- Shared packages stay narrow; no catch-all utility package.
- Package names must make the rules in [architecture enforcement](../../architecture/architecture-enforcement.md) expressible as ArchUnit tests.
- The layering in [layers and boundaries](../../architecture/layers-and-boundaries.md) applies inside each feature.
- A module that depends on infrastructure keeps its migrations in its own Flyway folder and has a configuration property that switches it off.
- Foundation modules and the example feature sit in stable, predictable locations, because applications merge upstream releases ([ADR 0023](../../decisions/0023-template-fork-adoption-model.md)).
- The frontend mirrors the backend with one feature folder per module.

## Not decided

- The names of the top-level packages and how a module separates its public contract from its internals.
- Where ports and adapters live inside a module.

These block [milestone 0](../../product/roadmap/milestone-0-hygiene-and-guardrails.md) and are tracked in [open questions](../../decisions/open-questions.md).
