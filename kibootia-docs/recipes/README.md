# Recipes

- **Type:** reference
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** not applicable (index).

Task-oriented guides for things a fork does on purpose: remove or switch off what ships by default, and add optional integrations ([ADR 0036](../decisions/0036-technology-catalogue-and-recipes-areas.md)). A recipe is "documented, not verified" unless CI covers it, and each says which. The first recipes are the removal recipes of [milestone 5](../product/roadmap/milestone-5-example-feature.md), which CI runs; the integration recipes named in the [technology catalogue](../technology/README.md#recipes-planned-none-written-yet) arrive with [milestone 6](../product/roadmap/milestone-6-production-readiness.md).

| Recipe | What it does | CI |
|---|---|---|
| [remove-notes.md](remove-notes.md) | Deletes the example feature, its table, screens and permissions with one script | `module-removal`, row "notes (removed)" |
| [disable-files.md](disable-files.md) | Starts the application with the object storage module off | `module-removal`, row "files (switched off)" |
| [disable-observability.md](disable-observability.md) | Starts the application with telemetry export off | `module-removal`, row "observability (switched off)" |

The row "notes, files and observability (all)" runs the three together.
