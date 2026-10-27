# Local Setup

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** observed, **not verified**. Written from `compose.yaml`, `application.properties` and `pom.xml`. No command below was executed while writing this document.

## Goal

Start the local database and the backend application.

## Prerequisites

- Docker with Compose.
- A JDK. The decided baseline is Java 21 ([ADR 0019](../../decisions/0019-java-21-baseline.md)); `pom.xml` still sets 17 today.
- No separate Maven install is needed; `backend/` has the Maven wrapper.

## What the Compose file provides (observed)

| Service | Purpose | Local access |
|---|---|---|
| `mysql` (MySQL 8.4) | Application database, database name `kibootia`, application user `kibootia_app` | Host port 3307 |
| `adminer` | Web database browser | Host port 8081 |

Keycloak is decided for the local environment ([ADR 0020](../../decisions/0020-keycloak-in-local-compose.md)) but is **not** in the Compose file yet.

## Environment variables (names only)

Compose requires these two variables and refuses to start without them. Define them in a `.env` file at the repository root, with values you choose locally. **Never commit values.**

| Variable | Used for |
|---|---|
| `MYSQL_ROOT_PASSWORD` | Root password of the local MySQL |
| `MYSQL_PASSWORD` | Password of the `kibootia_app` database user |

## Steps

1. Create the root `.env` with the two variables above.
2. From the repository root, start the database: `docker compose up -d`.
3. Run the backend from `backend/` with `./mvnw spring-boot:run` (`mvnw.cmd` on Windows).

## Known gap

The datasource password is currently hardcoded in `backend/src/main/resources/application.properties`, so it must equal `MYSQL_PASSWORD` for the backend to connect. This is the situation the [secrets convention](../../operations/security/secrets-management.md) is meant to replace; once externalised, this guide will name the variable the backend reads.

## Verifying the result

Not yet defined. There is no health endpoint ([observability baseline](../../operations/monitoring/observability-baseline.md)), and a clean-clone run has not been recorded.

## Related

[Application configuration](../../operations/configuration/application-configuration.md).
