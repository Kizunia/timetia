# API Conventions

- **Type:** api
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** planned. `spring-boot-starter-validation` and `spring-boot-starter-webmvc` are in `pom.xml`; no endpoints or conventions exist.

## Purpose

Fix the topics on which the API behaves consistently, so the decisions are made once. Global conventions belong to shared API infrastructure; business behavior stays with the owning feature.

## Topics covered (owner direction)

Request validation; error responses; HTTP status codes; authentication failures; authorization failures; application-level failures; logging and sensitive-data handling.

## Decided

- The backend validates all input; client-side validation never replaces it.
- Authentication failures and authorization failures are distinguishable.
- DTOs, not entities, form the API contract ([model separation](../data/model-separation.md)).
- The frontend depends on these contracts only, through a client generated from the backend's OpenAPI description ([ADR 0038](../../decisions/0038-frontend-stack.md)). The OpenAPI description is generated, and an interactive documentation page is available locally and secured or disabled in production.
- A concurrent-edit conflict returns `409 Conflict` ([persistence stack](../data/persistence-stack.md)).
- Lists use offset paging with a maximum page size; keyset paging is documented for large tables.
- There is no CORS policy for browser traffic, because browsers call only the Next.js server ([ADR 0026](../../decisions/0026-nextjs-bff-session.md)).
- Sensitive endpoints (invitations, uploads, administrative actions) are rate limited per application instance. Production must also limit at the edge or gateway. Keycloak's own brute-force detection covers login.

## Not decided (do not assume)

| Topic | Status |
|---|---|
| Error response format (for example a standard problem-details shape) | Open |
| Validation error structure | Open |
| Status code mapping per failure class | Open |
| Sorting and filtering conventions | Open |
| API versioning and URL layout | Open |

All are tracked in [open questions](../../decisions/open-questions.md) and belong to [milestone 2](../../product/roadmap/milestone-2-platform-conventions.md).

## Security considerations

Error responses and logs must not leak secrets, tokens or internal details.

## Testing

API tests for each convention are expected; none exist.
