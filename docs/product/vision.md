# Vision

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (vision). The starter is at the very beginning; see [implementation status](roadmap/implementation-status.md).

Source: owner decision recorded in the project definition (`docs/temp/kibootia-project-definition.md`, sections 1 to 3).

## What Kibootia is

Kibootia is a reusable, enterprise-oriented application starter built around **Spring Boot** (backend) and **Next.js** (frontend). It is the foundation from which several independent applications can be built. A consuming application brings its own business domain, modules, data models, workflows and screens, and inherits a consistent technical base.

It is not a business application, a hosted product, or a replacement for Spring Boot or Next.js.

## The problem

Each new application re-answers the same questions: how to organise the backend, where business logic lives, how authentication and authorization work, how the schema evolves, how API errors behave, how secrets and configuration are handled, how to test, and how to keep architecture from decaying. Different answers per project mean uneven quality and inconsistent security.

Kibootia answers these once, writes down both the answer and the reason, and enforces the important parts with automated checks.

## Who it is for

Developers and small teams building full-stack applications on this stack, and teams who want the same backend architecture across several projects. These are intended audiences; there is no existing user base.

## What a successful start looks like

A developer starts a new application and inherits clear package conventions, explicit module contracts, enforceable dependency rules, working security integration, reliable migrations, consistent API behavior, validated configuration and tests, without rebuilding that architecture. The detailed list is in [goals and non-goals](goals-and-non-goals.md).

## Where it may go later

Possible future capabilities are notifications, file storage, durable messaging, advanced observability and organization-aware applications. These are possibilities, not commitments, and are added only when a concrete requirement appears.

## Related

- [Architecture overview](../architecture/overview.md)
- [Goals and non-goals](goals-and-non-goals.md)
- [Glossary](../shared/README.md)
