# Feature

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (definition).

A unit of application behavior in the backend. A feature owns its application behavior, its public contracts and its feature-specific infrastructure, including its persistence. Other features use its public contract only, never its private classes or repositories. Kibootia ships no business features; consuming applications add them. The word "module" in the project definition refers to this idea inside the [modular monolith](modular-monolith.md).

Note: a docs folder `modules/` may be created to document such modules once they exist; it is optional and does not exist yet (see [documentation standards](../../meta/documentation-standards.md#12-scope-and-exceptions)).
