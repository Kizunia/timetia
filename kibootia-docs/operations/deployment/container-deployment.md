# Container Deployment (provisional)

- **Type:** spec
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-10-27

> **Implementation:** planned. No Dockerfile or deployment configuration exists.

## Scope

Owner choice (provisional): document local development and a generic container deployment. Staging and production environment definitions are not part of this set, and the hosting platform is left to each consuming application. Kubernetes is not an initial requirement.

## Model

- The backend is packaged as a container image and configured entirely through environment variables or mounted files, identical in shape to local configuration ([application configuration](../configuration/application-configuration.md)).
- Production secrets are delivered by the platform from Vault ([secrets management](../security/secrets-management.md)).
- The database schema is changed only by Flyway migrations ([schema migrations](../../architecture/data/schema-migrations.md)).

## Not decided

Image build method, base image, the Java version in the image (follows [ADR 0019](../../decisions/0019-java-21-baseline.md)), how the frontend is deployed, when migrations run during rollout, and the health check contract. All in [open questions](../../decisions/open-questions.md).
