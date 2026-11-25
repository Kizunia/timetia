# Glossary

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (index).

| Term | Summary |
|---|---|
| [adapter](adapter.md) | Infrastructure code that implements a port |
| [application-module](application-module.md) | A sub-package of the root package with a public contract and private internals |
| [authorization-scope](authorization-scope.md) | ALL, OWN or NONE: which records a caller may see or change |
| [backend-for-frontend](backend-for-frontend.md) | The Next.js server that signs users in and holds their tokens |
| [capability-tier](capability-tier.md) | Core, default-on, optional, recipe or future |
| [definition-of-done](definition-of-done.md) | The checks a feature must pass before it counts as complete |
| [feature](feature.md) | A unit of application behavior owned by one part of the backend |
| [hexagonal-architecture](hexagonal-architecture.md) | Application logic depends on ports, not on infrastructure |
| [modular-monolith](modular-monolith.md) | One deployable application with enforced internal modules |
| [platform-package](platform-package.md) | Narrow shared package for genuinely cross-cutting capability |
| [policy-decision-point](policy-decision-point.md) | The single place that decides whether an action is allowed |
| [port](port.md) | An application-owned interface for a needed capability |
| [problem-details](problem-details.md) | The standard JSON error shape, with Kibootia extensions |
| [realm-role](realm-role.md) | A Keycloak role defined at realm level |
| [recipe](recipe.md) | A guide for integrating an optional technology |
| [resource-server](resource-server.md) | A backend that validates bearer access tokens |
| [soft-delete](soft-delete.md) | Marking a record deleted, restorable until a retention purge |
| [tenant](tenant.md) | One customer organization served by an application |
| [transactional-outbox](transactional-outbox.md) | Events saved in the same transaction as the change, delivered afterwards |

The glossary is split into letter folders when it passes about 100 terms.
