# Goals and Non-Goals

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** not applicable (scope statement). Progress per capability: [implementation status](roadmap/implementation-status.md).

Source: owner decisions in the project definition (sections 2, 4, 11, 12).

## Goals

1. A reliable technical foundation for future applications.
2. Architectural decisions written as explicit conventions, not assumptions.
3. Business logic independent of unnecessary infrastructure details.
4. Consistent security, persistence, API, configuration and testing practices.
5. Important architecture rules enforced by automated tests ([architecture enforcement](../architecture/architecture-enforcement.md)).
6. Reusable capabilities that stay free of any consuming application's domain.
7. Documentation that explains how the project works and why.

## Principles

- **No domain assumptions.** Nothing specific to one business domain enters the core.
- **Architecture as an enforceable contract.**
- **Explicit boundaries** between API contracts, use cases, domain rules, persistence, infrastructure, shared platform capabilities and the frontend. See [layers and boundaries](../architecture/layers-and-boundaries.md).
- **Secure by default.** The backend is authoritative for authentication and authorization; frontend checks are for usability only.
- **Separation of concerns.** Persistence entities are not API responses; shared packages do not become dumping grounds.
- **Enterprise-grade without unnecessary complexity.** Every dependency, abstraction and service has a stated purpose.
- **Documentation-first.** See [living documentation](../meta/living-documentation.md).
- **Incremental delivery** in verified [milestones](roadmap/README.md).

## Non-goals (not initial requirements)

Microservices; Kubernetes; Kafka or RabbitMQ; a universal event framework; a complete ABAC engine; organization-management UI; a full tenant-management subsystem; MinIO or dedicated object storage; Elasticsearch or Kibana; a full Prometheus/Grafana stack; universal mapping or repository frameworks; separate Maven modules per feature; business-domain models.

Each may be reconsidered when a concrete requirement appears, through a new ADR.

## Success criteria

The initial foundation succeeds when it has: a clear package structure; explicit module contracts; enforceable dependency rules; a working security integration; reliable persistence and migrations; separate application and persistence models; consistent API behavior; validated configuration and safe secret handling; automated architecture and behavior tests; a documented frontend integration path; and clear instructions for local development and extension.

Success is not measured by the number of technologies installed. A criterion counts as met only when it is [verified](../engineering/testing/verification-standard.md).
