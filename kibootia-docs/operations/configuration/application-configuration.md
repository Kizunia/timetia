# Application Configuration

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** observed, and short of the target below.

## Principles (owner decisions)

- Local development uses environment variables and Docker Compose.
- Production secrets arrive as environment variables or mounted files; Vault is a recommended source ([secrets management](../security/secrets-management.md), [ADR 0032](../../decisions/0032-secrets-from-environment-and-files.md)).
- Configuration is typed and validated where practical. A missing required setting fails startup clearly.
- Production never silently falls back to development values; startup fails if it detects development defaults.
- Local secrets are generated per developer from a safe example environment file, and real values are never committed.
- Each infrastructure-backed module has an on/off property ([ADR 0024](../../decisions/0024-package-modules-with-infrastructure-toggles.md)).

## Settings that exist today (observed)

Names only; values are not documented here.

| Setting | Where | Notes |
|---|---|---|
| `spring.application.name` | `application.properties` | Set to the project name |
| `spring.datasource.url` | `application.properties` | Points at the local Compose MySQL |
| `spring.datasource.username` | `application.properties` | The local application database user |
| `spring.datasource.password` | `application.properties` | Hardcoded; does not meet the target |
| `spring.jpa.hibernate.ddl-auto` | `application.properties` | Set to `validate`; effective only once JPA is added |
| `MYSQL_ROOT_PASSWORD`, `MYSQL_PASSWORD` | Root `.env`, read by `compose.yaml` | Required by Compose |

## Proposed approach (not decided)

Standard Spring Boot behavior lets environment variables override properties, so the datasource password can be supplied by the environment without a code change. Typed configuration properties with validation for Kibootia's own settings follow the "typed and validated" principle. The naming scheme for Kibootia-specific settings and the full variable catalog are open ([open questions](../../decisions/open-questions.md)).

## Not configured yet

Keycloak issuer and audience, the Next.js client and administration service-account secrets, storage endpoint and credentials, OTLP endpoint, mail settings, module on/off properties, the bootstrap administrator setting, logging format, health exposure, profiles per environment.

## Related

[Local setup](../../engineering/getting-started/local-setup.md), [milestone 0](../../product/roadmap/milestone-0-hygiene-and-guardrails.md).
