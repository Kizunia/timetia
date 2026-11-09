# Local Setup

- **Type:** guide
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-09

> **Implementation:** the steps below were run on a Windows machine on 2026-11-09 with the host-run applications (`pnpm infra:up`, `pnpm dev`) and a Playwright sign-in test. They were **not** run on macOS or Linux, **not** run from a fresh clone (see the status of the `apps` profile below), and CI does not run this guide. Nothing here is **verified** in the sense of the [verification standard](../testing/verification-standard.md) until the pipeline has run it.

Get from a clone to a signed-in user whose request shows up as one trace across the web app and the API. Local demo credentials, exposed ports and permissive settings apply to local development only and never carry into production ([container deployment](../../operations/deployment/container-deployment.md)).

## Prerequisites

- Docker with Compose, running. About 8 GB of RAM is enough (the infrastructure uses roughly 3 to 4 GB).
- A Java 25 JDK, Eclipse Temurin recommended ([ADR 0048](../../decisions/0048-java-25-baseline-on-temurin.md)). No separate Maven install is needed; `backend/` has the Maven wrapper.
- Node 22 or later and pnpm 11 (`corepack enable` or `npm install --global pnpm@11.12.0`).

## Steps

Run these from the repository root.

1. `pnpm install` installs the web app's dependencies.
2. `pnpm bootstrap` creates `.env` from `.env.example` with freshly generated secrets. Running it again only adds variables that `.env.example` has gained since; it never changes a value you already have. (The script is named `bootstrap` because `pnpm setup` is pnpm's own built-in command.)
3. `pnpm infra:up` starts MySQL, Keycloak with its PostgreSQL, the one-shot OpenTofu step that applies the realm, Mailpit, Adminer and Grafana LGTM. The first start downloads several images and takes a few minutes.
4. `pnpm dev` starts the backend and the Next.js server on the host. Stop it with Ctrl+C.
5. Open <http://localhost:3470>, choose **Sign in**, and use the username `demo` with the password in `DEMO_USER_PASSWORD` in `.env`.

After signing in, **See who the API thinks you are** shows the same identity twice: once rendered on the server from a direct call to Spring, and once fetched by the browser through the `/api` proxy.

To start again from nothing, `pnpm reset` removes every local volume (databases, the realm state, Grafana data) and starts the infrastructure again. Your `.env` is kept.

### Addresses

| What | Address |
|---|---|
| Web app | <http://localhost:3470> (`/healthz` for liveness) |
| API | <http://localhost:8080> (every endpoint needs a token) |
| API health | <http://127.0.0.1:8090/actuator/health> (management port, this machine only) |
| Keycloak | <http://localhost:8180> (realm `kibootia`; administrator `admin`, password `KEYCLOAK_ADMIN_PASSWORD`) |
| Grafana | <http://localhost:3001> (traces in Tempo; the default login is `admin` / `admin`) |
| Mailpit | <http://localhost:8025> |
| Adminer | <http://localhost:8081> (server `mysql`) |
| MySQL | `localhost:3307` |

These ports were chosen to avoid the ones other local projects commonly use. If one is taken, the corresponding service fails to start rather than moving to another port, because the sign-in redirect URIs depend on the web app's port.

### Running the applications in containers

`pnpm apps:up` builds the two images and starts both applications in the `apps` Compose profile; `pnpm apps:down` stops them. Stop `pnpm dev` first, because both use ports 8080 and 3470. Inside Compose the applications reach Keycloak as `http://keycloak:8080`, while browsers and tokens use `http://localhost:8180`.

### Tests

| Command | What it runs | Needs |
|---|---|---|
| `./mvnw verify` in `backend/` | Unit, ArchUnit, Modulith and Testcontainers tests | Docker |
| `pnpm --filter kibootia-frontend lint`, `typecheck`, `test` | Lint, type check and the Vitest unit tests | Nothing running |
| `pnpm --filter kibootia-frontend test:e2e` | The Playwright sign-in test | Infrastructure and the applications running (`pnpm dev` or `pnpm apps:up`); first run `pnpm --filter kibootia-frontend exec playwright install chromium` |

## Troubleshooting

- **A port is already in use.** Find what holds it before stopping anything: it may belong to another project.
- **`MYSQL_PASSWORD` or another setting is missing.** Run `pnpm bootstrap`.
- **Your `.env` is from before milestone 0** and its `MYSQL_PASSWORD` no longer matches the password your MySQL volume was created with: run `pnpm reset`.
- **Sign-in says the attempt expired.** The sign-in cookie lives ten minutes and only works in the browser that started it; start again from the home page.
- **Docker is not running.** `pnpm infra:up` and the backend tests need it.

## Related

[Application configuration](../../operations/configuration/application-configuration.md), [milestone 1](../../product/roadmap/milestone-1-walking-skeleton.md), [ADR 0040](../../decisions/0040-local-development-topology-and-setup.md).
