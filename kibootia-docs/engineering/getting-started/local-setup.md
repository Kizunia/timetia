# Local Setup

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** the steps under "Current steps" were run on a Windows machine on 2026-11-08 for the tests, `pnpm bootstrap` and `docker compose config`; a full clean-clone run (start Compose, start the backend against it) was **not** recorded, and CI does not cover this guide. The "Target fresh-clone journey" is planned.

## Current steps

### Goal

Start the local database and the backend application.

### Prerequisites

- Docker with Compose.
- A Java 25 JDK, Eclipse Temurin recommended ([ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md)); `pom.xml` sets `java.version` 25.
- Node 22 or later, for `pnpm bootstrap`.
- Docker must be running to run the backend tests (Testcontainers starts MySQL).
- No separate Maven install is needed; `backend/` has the Maven wrapper.

### What the Compose file provides (observed)

| Service | Purpose | Local access |
|---|---|---|
| `mysql` (MySQL 8.4) | Application database, database name `kibootia`, application user `kibootia_app` | Host port 3307 |
| `adminer` | Web database browser | Host port 8081 |

Keycloak is decided for the local environment ([ADR 0020](../../decisions/0020-keycloak-in-local-compose.md)) but is **not** in the Compose file yet.

### Environment variables (names only)

Compose requires both variables and refuses to start without them; the backend reads `MYSQL_PASSWORD` and stops with a message naming the setting if it is missing. `pnpm bootstrap` creates `.env` at the repository root from `.env.example` with generated random values, and does not overwrite an existing `.env`. **Never commit values.**

| Variable | Used for |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Root password of the local MySQL |
| `MYSQL_PASSWORD` | Password of the `kibootia_app` database user |

### Steps

1. From the repository root, run `node scripts/bootstrap.mjs` (or `pnpm bootstrap`) to create `.env`.
2. Start the infrastructure: `docker compose up -d` (or `pnpm infra:up`).
3. Run the backend from `backend/` with `./mvnw spring-boot:run` (`mvnw.cmd` on Windows). It imports the root `.env` automatically.
4. Run the tests with `./mvnw verify` from `backend/` (Docker required).

### Known gap

If you have a `.env` from before milestone 0, its `MYSQL_PASSWORD` must match the password your MySQL volume was created with; delete the volume (`docker compose down -v`) to start over with generated values.

### Verifying the result

Not yet defined. There is no health endpoint ([observability baseline](../../operations/monitoring/observability-baseline.md)), and a clean-clone run has not been recorded.

## Target fresh-clone journey (planned)

What must work for the foundation to count as usable by a new developer (set by the owner on 2026-10-28, refined on 2026-11-08 by [ADR 0040](../../decisions/0040-local-development-topology-and-setup.md) and [ADR 0045](../../decisions/0045-keycloak-operations.md); none of it exists yet).

**Prerequisites:** Docker with Compose; a Java 25 JDK (Eclipse Temurin); Node LTS with pnpm; about 8 GB of RAM (the infrastructure uses roughly 3 to 4 GB). Windows, macOS and Linux are supported the same way.

1. Clone the repository and add Kibootia as the `upstream` remote if you want release merges ([ADR 0023](../../decisions/0023-template-fork-adoption-model.md)).
2. Run `pnpm bootstrap`. It copies `.env.example` to `.env` and generates random local secrets. Configuration is validated at startup with a clear failure when something is missing.
3. Run `pnpm infra:up`. Compose starts MySQL, Keycloak with its PostgreSQL, a one-shot OpenTofu step that applies the realm (base plus local overlay), the object storage server, the mail catcher, Grafana LGTM and Adminer. Health checks and startup ordering are in place, and service URLs are documented.
4. Run `pnpm dev` to start the backend and the Next.js server on the host. Flyway migrations and local seed data run at startup. Alternatively, the `apps` Compose profile runs both applications in containers for a demo without a local JDK or Node.
5. Sign in with a documented demo account, including a demo administrator.
6. In the reference application: manage users, roles and invitations, view the audit log, edit a profile and upload an avatar, and try the example feature.
7. See logs, metrics and traces for those actions in Grafana, and open the generated API documentation.
8. Troubleshooting notes cover the common failures.
9. `pnpm reset` removes local volumes (databases, storage, OpenTofu state) and seeds again.

Local demo credentials, exposed ports and permissive settings apply to local development only and never carry into production ([container deployment](../../operations/deployment/container-deployment.md)). Roadmap: [milestone 1](../../product/roadmap/milestone-1-walking-skeleton.md) delivers the first working chain.

## Related

[Application configuration](../../operations/configuration/application-configuration.md).
