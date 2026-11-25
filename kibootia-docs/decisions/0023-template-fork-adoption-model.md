# 0023 Template and fork adoption model

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. No release tags or changelog exist yet.

## Context

Kibootia is meant to be the foundation for several applications, and the owner wants some capabilities to be replaceable or removable. How a consuming application obtains Kibootia, and how it later receives fixes, decides what "replace" and "optional" mean in practice. The question was open as OQ-23.

## Decision

Consuming applications adopt Kibootia by **copying the repository and owning all of the code**. Kibootia publishes no libraries or packages.

An application starts by cloning the repository, keeps its history, and adds Kibootia as an `upstream` remote. It upgrades by merging a tagged release. Kibootia publishes SemVer release tags and a changelog that marks security fixes and gives upgrade notes. Commit messages follow Conventional Commits so the changelog can be generated.

Replacing a capability means editing or deleting code the application owns. Disabling one needs the explicit mechanism in [ADR 0024](0024-package-modules-with-infrastructure-toggles.md).

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Hybrid: stable platform parts published as Maven or npm packages | Requires a stable public API and release engineering from day one; remains possible later if boundaries are clean |
| Shared library or platform dependency | Strongest upgrade path but the most effort, and harder deep customization |
| Generator or CLI | Generated code is copied, so it has the same upgrade problem as a template, plus a generator to maintain |
| GitHub template button (clean history) | Upgrades become manual porting from the changelog |

## Consequences

- Applications fully control their code, and upgrades are merges that can conflict. Conflicts grow as an application diverges.
- Kibootia must keep foundation code in predictable, stable locations, publish releases, and write upgrade notes. Nothing pushes fixes to applications.
- The documentation is already fork-ready ([portability](../meta/documentation-standards.md#10-portability-across-forks)).

## Status

accepted. Resolves OQ-23.

## Related

[Context and boundaries](../architecture/context-and-boundaries.md), [ADR 0002](0002-modular-monolith.md), [ADR 0024](0024-package-modules-with-infrastructure-toggles.md).
