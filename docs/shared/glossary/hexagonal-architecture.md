# Hexagonal Architecture

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (definition).

A style in which application logic defines what it needs as [ports](port.md) and infrastructure supplies [adapters](adapter.md), so that dependencies point inward toward the application. In Kibootia it is applied only where it protects a meaningful boundary; not every class needs an interface. Decision: [ADR 0003](../../decisions/0003-hexagonal-architecture-where-boundaries-matter.md).
