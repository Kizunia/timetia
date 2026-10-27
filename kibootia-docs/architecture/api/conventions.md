# API Conventions

- **Type:** api
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. `spring-boot-starter-validation` and `spring-boot-starter-webmvc` are in `pom.xml`; no endpoints or conventions exist.

## Purpose

Fix the topics on which the API behaves consistently, so the decisions are made once. Global conventions belong to shared API infrastructure; business behavior stays with the owning feature.

## Topics covered (owner direction)

Request validation; error responses; HTTP status codes; authentication failures; authorization failures; application-level failures; logging and sensitive-data handling.

## Decided

- The backend validates all input; client-side validation never replaces it.
- Authentication failures and authorization failures are distinguishable.
- DTOs, not entities, form the API contract ([model separation](../data/model-separation.md)).
- The frontend depends on these contracts only.

## Not decided (do not assume)

| Topic | Status |
|---|---|
| Error response format (for example a standard problem-details shape) | Open |
| Validation error structure | Open |
| Status code mapping per failure class | Open |
| Pagination, sorting, filtering | Open |
| API versioning and URL layout | Open |
| CORS policy | Open; depends on the login flow ([authentication](../identity/authentication.md)) |

All are tracked in [open questions](../../decisions/open-questions.md) and belong to [milestone 5](../../product/roadmap/milestone-5-api-and-operational-conventions.md).

## Security considerations

Error responses and logs must not leak secrets, tokens or internal details.

## Testing

API tests for each convention are expected; none exist.
