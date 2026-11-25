# Backend-for-frontend (BFF)

- **Type:** reference
- **Module:** shared
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-28

> **Implementation:** not applicable (definition).

A server that sits between a browser application and the backend API. In Kibootia the Next.js server is the BFF: it signs the user in with Keycloak, keeps the tokens on the server, gives the browser only a session cookie, and calls the Spring backend with a bearer token. Decision: [ADR 0026](../../decisions/0026-nextjs-bff-session.md).
