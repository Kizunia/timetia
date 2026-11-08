# 0039 Spring Modulith for module boundaries and the event outbox

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. The repository has one package, `com.kibootia.backend`, and no Spring Modulith dependency.

## Context

[ADR 0024](0024-package-modules-with-infrastructure-toggles.md) makes modules Java packages checked by tests, but left open (OQ-21) which modules exist, how a module separates its public contract from its internals, and where ports live. [ADR 0031](0031-transactional-outbox-and-locked-jobs.md) requires a transactional outbox and left the implementation open between Spring Modulith's event publication registry and a hand-written table (OQ-43). Every class written from milestone 0 onward lands in this layout, and forks merge upstream releases ([ADR 0023](0023-template-fork-adoption-model.md)), so moving packages later is costly for everyone.

Research on 2026-11-08: the Spring Modulith 2.1.x line targets Spring Boot 4.1, and 2.1.1 was released in August 2026. That shows a compatible line exists; it does not prove that the build works with the exact versions in use.

## Decision

**Spring Modulith** verifies module boundaries, and its **event publication registry is the transactional outbox**. ArchUnit ([ADR 0013](0013-archunit-architecture-enforcement.md)) keeps the layer rules inside each module.

- Each direct sub-package of the fixed root package `com.kibootia.backend` is an application module. **Forks never rename the root package**; renaming would turn every upstream merge into conflicts. Forks add their own modules beside the foundation modules.
- Types in a module's top-level package are its **public contract** (API interfaces, records, events). Sub-packages are private by default; a sub-package is exposed only through an explicit named interface.
- Inside a module: `application/` (use cases and outbound ports), `domain/` (only where business rules warrant it), `web/` (controllers and HTTP DTOs), `persistence/` (entities, repositories, port adapters), and adapter packages named after the external system (for example `keycloak/`).
- **Module map:**

| Module | Responsibility |
|---|---|
| `platform` | Shared plumbing only: security configuration, error handling, tenancy, persistence base types (auditing, soft delete), job and event configuration, observability. No business logic |
| `identity` | User profiles, `/me`, user administration through the Keycloak Admin API port, the avatar use case |
| `access` | Permission registry, roles, role assignments and the decision point ([ADR 0027](0027-central-authorization-decision-point.md)) |
| `audit` | The append-only audit log and its viewer ([ADR 0046](0046-audit-log-design.md)) |
| `files` | Generic file storage: S3 port, metadata, validation, image processing, reconciliation job ([ADR 0029](0029-s3-object-storage-contract.md)) |
| `notes` | The deletable example feature (working name) |

- `access` is separate from `identity` so that every module can check permissions without depending on the replaceable user-administration module. Every module may depend on `platform`. There is no mail module ([ADR 0041](0041-user-lifecycle-and-account-model.md)).
- The registry's table is created by a Flyway migration, not by Spring Modulith's schema initialisation ([ADR 0012](0012-flyway-managed-schema-evolution.md)). Tenant and trace context travel inside the event payload, because the registry stores the event as serialised data in its own table.
- **Verification gate (milestone 0):** the build with the pinned Spring Boot 4.1.x and Spring Modulith 2.1.x must pass, including the registry on MySQL. If it fails, the fallback is Spring Modulith for boundary checks only plus a hand-written outbox table.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Spring Modulith for boundary checks only, hand-written outbox | Full control of the outbox schema, but several hundred lines of reliability-critical retry and locking code to own and test; kept as the fallback |
| ArchUnit only, hand-written module rules and outbox | No new framework, but rebuilds what Spring Modulith provides and leaves the most custom code |
| One `users` module containing identity and access | Simpler, but every module would depend on the whole replaceable user-administration module |

## Consequences

- One more framework dependency whose minor version must track Spring Boot's (2.1.x with Boot 4.1.x), handled by the [version policy](0035-version-adoption-policy.md).
- The failed-event view of ADR 0031 can be built on the registry's API for incomplete publications.
- ArchUnit rules about modules and cycles ([architecture enforcement](../architecture/architecture-enforcement.md)) are largely covered by Spring Modulith's verification; ArchUnit concentrates on layers inside modules.
- Module-scoped integration tests become available.

## Status

accepted. Resolves OQ-21 and the decision part of OQ-43 (the build check remains). Extends [ADR 0013](0013-archunit-architecture-enforcement.md), [ADR 0024](0024-package-modules-with-infrastructure-toggles.md) and [ADR 0031](0031-transactional-outbox-and-locked-jobs.md), which stay accepted.

## Related

[Package structure](../engineering/conventions/package-structure.md), [layers and boundaries](../architecture/layers-and-boundaries.md), [communication](../architecture/communication.md).
