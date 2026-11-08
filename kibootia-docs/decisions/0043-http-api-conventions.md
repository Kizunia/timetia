# 0043 HTTP API conventions

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. No endpoints, error handling or OpenAPI generation exist.

## Context

[API conventions](../architecture/api/conventions.md) left the error format, validation errors, status mapping, URL layout, versioning, sorting and filtering open (OQ-26). [ADR 0038](0038-frontend-stack.md) requires a typed frontend client generated from the backend's OpenAPI description, but not how that description is produced. Every endpoint and every screen depends on these rules.

## Decision

**Contract source: code-first.** springdoc-openapi (the v3 line, which supports Spring Boot 4; exact version pinned at implementation) generates the OpenAPI description from controllers and DTOs. A test writes it to a committed `openapi.json`; the frontend client is generated from that file; CI fails when the committed file is out of date.

**Errors:** RFC 9457 Problem Details, with Kibootia extensions:

- `code`: a stable machine-readable code, `<module>.<reason>` (for example `access.last_admin`);
- `traceId`: the trace ID of the request;
- `errors`: for validation failures, a list of `{field, code, message}`.

Error responses never contain stack traces, internal identifiers of infrastructure, secrets or tokens.

**URLs:** everything under `/api/v1/`; plural kebab-case nouns (`/api/v1/users/{id}`); actions that are not plain create, read, update or delete are sub-resources (`POST /api/v1/users/{id}/disable`). JSON uses camelCase, timestamps are ISO-8601 in UTC, identifiers are UUID strings. A new major version is introduced only for a breaking change that matters to non-browser clients; Kibootia's own frontend ships with the backend.

**Status codes:**

| Situation | Status |
|---|---|
| Malformed request or failed validation | 400, with `errors` |
| Missing or invalid token | 401 |
| Authenticated but not allowed, including email not verified (`email_not_verified`) | 403 |
| Not found, **or** exists but the caller may not see it (another tenant, no view permission) | 404, so existence is not revealed |
| Concurrent-edit conflict, duplicate, or a business-state rule (for example removing the last administrator) | 409 |
| Upload too large / unsupported type | 413 / 415 |
| Rate limited | 429 with `Retry-After` |
| Unexpected failure | 500, generic message with `traceId` |

**Lists:** response `{ items, page: { number, size, totalElements, totalPages } }`; parameters `page`, `size` (default 20, maximum 100) and `sort=field,asc|desc`, where sortable fields are an allow-list per endpoint; filters are named query parameters per endpoint. There is no generic filter language.

**Documentation UI:** Swagger UI, bundled with springdoc, is enabled locally and disabled in production by default.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Code-first without committing the description | No reviewable diff; frontend builds depend on a running backend |
| Design-first (hand-written OpenAPI, generated server interfaces) | Slower daily work; suited to externally designed public APIs |
| Custom error envelope | Non-standard; every tool and fork must learn it |
| No version segment | No escape hatch once external clients exist |
| 422 for business-rule violations | The owner accepted 409 for all state conflicts |

## Consequences

- The committed `openapi.json` is part of every API change and is reviewed in pull requests.
- The frontend maps `code` values to messages; the backend's `message` is a developer-facing default.

## Status

accepted. Resolves OQ-26.

## Related

[API conventions](../architecture/api/conventions.md), [frontend architecture](../architecture/frontend.md), [ADR 0042](0042-bff-api-proxy-and-csrf.md).
