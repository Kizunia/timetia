# 0040 Local development topology and setup tooling

- **Type:** adr
- **Module:** platform
- **Status:** accepted
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-08

> **Implementation:** partly observed. `pnpm setup` and `pnpm infra:up` exist, `.env.example` exists and Adminer is pinned to 6.3.0. `pnpm dev`, `pnpm reset`, the `apps` profile and the Dockerfiles do not exist yet.

## Context

The fresh-clone journey ([local setup](../engineering/getting-started/local-setup.md)) says Compose starts the infrastructure, but not where the backend and the Next.js server run, how a newcomer starts everything on Windows, macOS and Linux, or how local secrets are generated. One technical trap shapes the answer: every token names its issuer as a URL. The browser reaches Keycloak at a `localhost` address while containers reach it by service name, so apps running inside Compose see a different issuer from the one in the token unless Keycloak and Spring are configured for it.

## Decision

**Hybrid topology.**

- Infrastructure (MySQL, Keycloak with its PostgreSQL, object storage, mail catcher, Grafana LGTM, Adminer) runs in Docker Compose.
- For daily work the Spring backend and the Next.js server run on the host, for fast reload and IDE debugging.
- A Compose profile named `apps` builds and runs both applications in containers. It serves the one-command demo and end-to-end runs. It is delivered together with the first Dockerfiles ([ADR 0047](0047-health-exposure-and-container-images.md)), not left until milestone 6.
- In the `apps` profile, Keycloak uses a fixed public hostname with an internal back channel, and Spring validates the public issuer while fetching signing keys from the internal address. This must be verified by a test.

**Setup tooling.** Root-level pnpm scripts backed by small Node scripts, identical on every operating system:

| Script | Does |
|---|---|
| `pnpm setup` | Copies `.env.example` to `.env` and fills it with freshly generated random local secrets (database passwords, Keycloak client secrets, the session-cookie key) |
| `pnpm infra:up` | Starts the infrastructure |
| `pnpm dev` | Starts the backend and the Next.js server on the host |
| `pnpm reset` | Removes local volumes and seeds again |

**Prerequisites** documented for developers: Docker with Compose, a JDK of the chosen baseline ([ADR 0048](0048-java-25-baseline-on-temurin.md)), Node LTS with pnpm, and a machine with about 8 GB of RAM (the infrastructure needs roughly 3 to 4 GB).

**Adminer** stays, pinned to an exact version and started by default locally. It is a local convenience and never appears in a production Compose file.

## Alternatives considered

| Alternative | Why not chosen |
|---|---|
| Everything in Compose, always | One prerequisite, but slow file watching on Windows bind mounts, harder debugging and the issuer problem on every machine |
| Applications on the host only | Simplest, but no one-command demo; newcomers must install a JDK and Node first |
| `just` task runner | Clean and cross-platform, but one more tool to install |
| Makefile plus PowerShell scripts | Two scripts that drift apart |
| Remove Adminer, or start it only on request | The owner prefers it available by default locally |

## Consequences

- Two documented ways to run the applications.
- The setup script is the only supported way to create `.env`; real values are never committed ([ADR 0032](0032-secrets-from-environment-and-files.md)).
- Node and pnpm become prerequisites even for backend-only work, which they already were for the frontend.

## Status

accepted. Resolves OQ-14.

## Related

[Local setup](../engineering/getting-started/local-setup.md), [ADR 0020](0020-keycloak-in-local-compose.md), [ADR 0045](0045-keycloak-operations.md), [ADR 0021](0021-frontend-beside-backend-in-one-repository.md).
