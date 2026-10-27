# Application Configuration

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed, and short of the target below.

## Principles (owner decisions)

- Local development uses environment variables and Docker Compose.
- Production secrets come from Vault ([secrets management](../security/secrets-management.md)).
- Configuration is typed and validated where practical. A missing required setting fails startup clearly.
- Production never silently falls back to development values.

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

Keycloak issuer and audience, Vault delivery, CORS, logging format, health exposure, profiles per environment.

## Related

[Local setup](../../engineering/getting-started/local-setup.md), [milestone 2](../../product/roadmap/milestone-2-configuration-and-secrets.md).
