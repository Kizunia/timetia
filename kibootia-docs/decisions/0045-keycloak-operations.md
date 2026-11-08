# 0045 Keycloak operations: PostgreSQL, realm configuration with OpenTofu, production exposure

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** planned. Keycloak is not in `compose.yaml`; no realm configuration exists.

## Context

[ADR 0020](0020-keycloak-in-local-compose.md) runs Keycloak locally with an imported, version-controlled realm. Three things were not decided:

1. Keycloak needs its own database for users, sessions and realm settings.
2. Keycloak's realm import skips a realm that already exists, so later changes to the realm file (a new client, a mapper, the registration setting) never reach an existing developer environment or a running production server.
3. How Keycloak is reached in the [single-server reference deployment](0037-single-server-compose-reference-deployment.md), and whether its admin console is public.

Research on 2026-11-08: Keycloak lists MySQL 8.4 and PostgreSQL among its supported databases; keycloak-config-cli is maintained for Keycloak 26.x.

## Decision

**Database.** Keycloak uses **its own PostgreSQL server**, locally and in production. MySQL remains the application's database only. The PostgreSQL version is pinned from Keycloak's supported list.

**Realm configuration as code.** The realm is managed with **OpenTofu** and the Keycloak provider:

- OpenTofu runs as a **one-shot container**, in Compose locally and as a step of the production deployment, so no developer or operator installs it.
- Configuration has a shared base plus a local overlay (demo administrator, sample users) and a production overlay (no demo data). Secrets come from the environment ([ADR 0032](0032-secrets-from-environment-and-files.md)).
- The credentials OpenTofu uses are deploy-time only. They are not the backend's least-privilege service account ([ADR 0025](0025-identity-and-authorization-data-split.md)).
- **State** (OpenTofu's record of what it manages, which contains secrets in plain text): locally in a Docker volume that `pnpm reset` removes; in production in a private, encrypted bucket of the S3-compatible storage with OpenTofu's native locking; for deployments without S3, a root-only file on the server that is included in backups.

**Production exposure.** Keycloak's login pages are served on their own subdomain (`auth.<domain>`) through the TLS reverse proxy. The admin console is blocked at the proxy and reached by operators through an SSH tunnel; day-to-day user administration happens in Kibootia's own interface.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Separate database on the same MySQL server (the architect's recommendation) | The owner chose PostgreSQL for Keycloak |
| Keycloak's development database locally, a real one in production | Local and production would differ |
| keycloak-config-cli (the architect's recommendation) | The owner chose OpenTofu |
| Terraform | Business Source License; OpenTofu is the open-source (MPL 2.0) drop-in |
| Import only, wipe to apply changes | Impossible in production |
| Kibootia's backend configuring Keycloak at startup | Would need realm-administration rights, contrary to ADR 0025 |
| Keycloak under a path on the main domain | Path-routing quirks |
| Public admin console | Exposes a high-value target |

## Consequences

- Two database engines to run, monitor and back up. Production backups of MySQL and PostgreSQL must be taken consistently, or Keycloak users and Kibootia profiles can drift apart after a restore.
- The setup script also generates the Keycloak database password ([ADR 0040](0040-local-development-topology-and-setup.md)).
- Verification tasks: the OpenTofu Keycloak provider against the pinned Keycloak version, Keycloak on the pinned PostgreSQL, and native state locking on the chosen production S3 service.

## Status

accepted. Partly supersedes [ADR 0020](0020-keycloak-in-local-compose.md): the realm stays version-controlled and contains no real secrets, but it is applied by OpenTofu instead of a first-start import. ADR 0020 otherwise stays accepted.

## Related

[Authentication](../architecture/identity/authentication.md), [container deployment](../operations/deployment/container-deployment.md), [local setup](../engineering/getting-started/local-setup.md).
