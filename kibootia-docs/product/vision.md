# Vision

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (vision). The starter is at the very beginning; see [implementation status](roadmap/implementation-status.md).

Sources: the owner's project definition (`temp/kibootia-project-definition.md`) and the owner's decisions of 2026-10-28, recorded in the [decision log](../temp/kibootia-decision-log.md) and the ADRs from [0023](../decisions/0023-template-fork-adoption-model.md) onward.

## What Kibootia is

Kibootia is a reusable, enterprise-oriented application starter built around **Spring Boot** (backend) and **Next.js** (frontend). It is the foundation from which several independent applications can be built. A consuming application brings its own business domain, modules, data models, workflows and screens, and inherits a consistent technical base.

It is a **batteries-included** foundation, not a bare skeleton: the common capabilities of an enterprise application come with it and work together. Included does not mean mandatory. Each capability has a tier (always present, running by default, switched off by default, or documentation only), and applications can remove or replace what they do not need ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)).

It is not a business application, a hosted product, or a replacement for Spring Boot or Next.js.

## The problem

Each new application re-answers the same questions: how to organise the backend, where business logic lives, how authentication and authorization work, how users and files are managed, how the schema evolves, how API errors behave, how secrets and configuration are handled, how to observe a running system, how to test, and how to keep architecture from decaying. Different answers per project mean uneven quality and inconsistent security.

Kibootia answers these once, writes down both the answer and the reason, and enforces the important parts with automated checks.

## Who it is for

The owner's own future applications, and other developers and small teams building full-stack applications on this stack. These are intended audiences; there is no existing user base.

## How applications use it

An application clones the repository, keeps Kibootia as an `upstream` remote, owns all of the code, and upgrades by merging tagged releases ([ADR 0023](../decisions/0023-template-fork-adoption-model.md)). Kibootia publishes no libraries.

## What a fresh clone should offer

The target, not the present state, is that a developer can clone the repository, follow guided startup instructions, bring up the local infrastructure with Docker Compose, sign in, manage users and roles in an administration interface, change a profile photo, and see logs, metrics and traces for what they just did. The detailed steps belong in [local setup](../engineering/getting-started/local-setup.md), and what exists today is in [implementation status](roadmap/implementation-status.md).

A reference application demonstrates the foundation: sign-in, user and role administration, avatars, an audit log, invitations and API documentation, plus one deletable example feature that shows how to add a business feature. Business-domain assumptions never enter the foundation modules.

## What a successful start looks like

A developer starts a new application and inherits clear package conventions, explicit module contracts, enforceable dependency rules, working security integration, reliable migrations, consistent API behavior, validated configuration, observability and tests, without rebuilding that architecture. The detailed list is in [goals and non-goals](goals-and-non-goals.md).

## Where it may go later

Possible future capabilities are notifications beyond email, search, caching, feature flags, live multi-tenancy with organization management, a shipped policy engine and a shipped message broker. These are possibilities, not commitments, and are added only when a concrete requirement appears. Several are planned as documented recipes; see the [technology catalogue](../technology/README.md).

## Related

- [Architecture overview](../architecture/overview.md)
- [Goals and non-goals](goals-and-non-goals.md)
- [Roadmap](roadmap/README.md)
- [Glossary](../shared/README.md)
