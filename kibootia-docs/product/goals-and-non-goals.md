# Goals and Non-Goals

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** not applicable (scope statement). Progress per capability: [implementation status](roadmap/implementation-status.md).

Sources: owner decisions in the project definition (sections 2, 4, 11, 12), revised by the owner's decisions of 2026-10-28 ([decision log](../temp/kibootia-decision-log.md)). The earlier list of exclusions (object storage, a full observability stack, ABAC, user administration) no longer applies as written; the table at the end shows what changed.

## Goals

1. A reliable, batteries-included technical foundation for future applications.
2. Architectural decisions written as explicit conventions, not assumptions.
3. Business logic independent of unnecessary infrastructure details.
4. Consistent security, identity, persistence, file handling, API, configuration, observability and testing practices.
5. Important architecture rules enforced by automated tests ([architecture enforcement](../architecture/architecture-enforcement.md)).
6. Reusable capabilities that stay free of any consuming application's domain.
7. A working reference application and a fresh-clone developer experience that shows the capabilities working together.
8. Documentation that explains how the project works and why, including a technology catalogue and recipes.

## Principles

- **No domain assumptions.** Nothing specific to one business domain enters the foundation.
- **Architecture as an enforceable contract.**
- **Explicit boundaries** between API contracts, use cases, domain rules, persistence, infrastructure, shared platform capabilities and the frontend. See [layers and boundaries](../architecture/layers-and-boundaries.md).
- **Secure by default.** The backend is authoritative for authentication and authorization; frontend checks are for usability only. Local convenience never becomes a production default.
- **Included does not mean mandatory.** Every capability has a tier ([ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md)), and optional ones can be switched off or removed with a tested recipe.
- **Separation of concerns.** Persistence entities are not API responses; shared packages do not become dumping grounds.
- **Enterprise-grade without unnecessary complexity.** Every dependency, abstraction and service has a stated purpose, operating cost and replacement path.
- **Documentation-first.** See [living documentation](../meta/living-documentation.md).
- **Incremental delivery** in verified [milestones](roadmap/README.md). Delivery order is not scope: a capability being late does not make it out of scope.

## Capabilities

What the foundation includes, with its tier and selection status, is kept in one place: the [technology catalogue](../technology/README.md). Whether a capability exists or works is kept in [implementation status](roadmap/implementation-status.md). This document does not repeat either.

## Non-goals

Not part of the foundation, and not initial requirements:

- microservices;
- Kubernetes as a required platform;
- a shipped message broker (a broker is a recipe; durable events use the outbox of [ADR 0031](../decisions/0031-transactional-outbox-and-locked-jobs.md));
- a universal event framework;
- a shipped policy engine (OPA is a recipe, [ADR 0027](../decisions/0027-central-authorization-decision-point.md));
- an organization-management UI and a full tenant-management subsystem (the schema is tenant-ready, [ADR 0028](../decisions/0028-tenant-ready-schema.md));
- Elasticsearch or Kibana;
- a self-managed production telemetry stack (the local stack is shipped; a production backend is a recipe);
- universal mapping or repository frameworks;
- separate Maven modules for every feature;
- published libraries or a project generator ([ADR 0023](../decisions/0023-template-fork-adoption-model.md));
- business-domain models that do not belong to a generic starter;
- a backend mail module and application notifications (Keycloak sends identity email, [ADR 0041](../decisions/0041-user-lifecycle-and-account-model.md));
- interface translations (the reference application is English only);
- machine-to-machine API clients ([ADR 0044](../decisions/0044-authorization-enforcement-conventions.md)).

Each may be reconsidered when a concrete requirement appears, through a new ADR.

## What changed on 2026-10-28

| Earlier exclusion | Now |
|---|---|
| MinIO or dedicated object storage | Object storage is included through an S3 API contract, with RustFS locally ([ADR 0029](../decisions/0029-s3-object-storage-contract.md)); MinIO is not selected |
| A full Prometheus/Grafana stack | A local OTLP stack in one container is included ([ADR 0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md)) |
| A complete ABAC engine | Still not an engine; a permission model and decision point leave room for it ([ADR 0027](../decisions/0027-central-authorization-decision-point.md)) |
| Organization-management UI, tenant subsystem | Unchanged; the schema is tenant-ready |
| Kafka or RabbitMQ | Unchanged; durable events without a broker |

## Success criteria

The foundation succeeds when it has: a clear package structure; explicit module contracts; enforceable dependency rules; a working security and identity integration, including user and role administration; reliable persistence, migrations and file handling; separate application and persistence models; consistent API behavior; validated configuration and safe secret handling; observability that works locally; automated architecture and behavior tests; a working frontend sign-in and a fresh-clone path that is documented and tested; and clear instructions for local development, production deployment and extension.

Success is not measured by the number of technologies installed. A criterion counts as met only when it is [verified](../engineering/testing/verification-standard.md).
