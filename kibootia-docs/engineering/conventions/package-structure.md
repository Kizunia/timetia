# Package Structure

- **Type:** rules
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** observed. The six module packages exist under `com.kibootia.backend` as skeletons (a `package-info.java` with `@ApplicationModule` each); only `platform` has code (startup settings validation). No CI run yet.

Decisions: [ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md), [ADR 0039](../../decisions/0039-spring-modulith-module-boundaries-and-outbox.md), [ADR 0044](../../decisions/0044-authorization-enforcement-conventions.md).

## Root package

`com.kibootia.backend` is fixed. Applications built on Kibootia never rename it, because renaming would make every upstream merge conflict ([ADR 0023](../../decisions/0023-template-fork-adoption-model.md)). They add their own modules beside the foundation modules.

## Modules

Each direct sub-package of the root is a module, verified by Spring Modulith.

```text
com.kibootia.backend
├─ platform/   shared plumbing: security configuration, error handling, tenancy,
│              persistence base types, job and event configuration, observability
├─ identity/   profiles, /me, user administration (Keycloak Admin API port), avatar use case
├─ access/     permissions, roles, assignments, decision point
├─ audit/      append-only audit log and viewer
├─ files/      S3 port, file metadata, validation, image processing, reconciliation job
└─ notes/      deletable example feature (working name)
```

- Every module may depend on `platform`. `platform` holds no business logic.
- Modules depend on each other only through public contracts, and without cycles.

## Inside a module

```text
identity/
├─ IdentityApi.java, *.java      public contract: interfaces, records, events
├─ application/                  use cases (transaction and authorization boundary), outbound ports
├─ domain/                       only where business rules warrant a domain model
├─ web/                          controllers and HTTP DTOs
├─ persistence/                  JPA entities, repositories, port adapters
└─ keycloak/                     adapter named after the external system it talks to
```

- Types in the module's top-level package are its **public contract**. Sub-packages are private; a sub-package is exposed only through an explicit named interface.
- Ports needed by the module live in `application/`; adapters implementing them live in the adapter packages.
- Authorization checks sit on use-case methods in `application/` ([authorization](../../architecture/identity/authorization.md)).
- Permissions are declared as constants in the module, named `<module>.<resource>.<action>`.
- Not every module needs every package; add one only when it has content.

## Infrastructure-backed modules

A module that depends on infrastructure (today `files` and the observability configuration) also has an on/off property `kibootia.<module>.enabled`, its own Flyway migration folder and a Compose profile ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)). Every module keeps its migrations in its own folder.

## Frontend

The Next.js application mirrors the backend with one feature folder per module ([frontend](../../architecture/frontend.md)).

## Enforcement

Module boundaries: Spring Modulith. Layer and convention rules inside modules: ArchUnit ([architecture enforcement](../../architecture/architecture-enforcement.md)).
