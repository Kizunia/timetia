# Switch off telemetry export

- **Type:** recipe
- **Module:** platform
- **Status:** draft
- **Owner:** ishanKulkarni13
- **Last updated:** 2026-11-11

> **Implementation:** observed (local run, 2026-11-11; no CI run yet). The test configuration sets `kibootia.observability.enabled=false`, so every integration test runs the application with export off and it starts and answers. The CI job `module-removal` sets it through the environment and runs the `startup` group; that has not been run locally ([verification standard](../engineering/testing/verification-standard.md)).

## Problem

When there is no collector to send to, an application that keeps trying to export traces and metrics fills its logs with connection errors.

## When to use it, and when not to

Use it where no OTLP receiver is running, such as a laptop without the `observability` profile or a small deployment that does not collect telemetry. Do not use it in an environment that is supposed to be monitored; health and logs still work with it off, but traces and metrics do not leave the process.

## Prerequisites and compatibility

Written for milestone 5. The catalogue row is "OpenTelemetry starter, Micrometer, Actuator" ([ADR 0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md)).

## Steps

1. Set the switch in the environment of the backend:

   ```sh
   KIBOOTIA_OBSERVABILITY_ENABLED=false
   ```

2. Take `observability` out of `COMPOSE_PROFILES` in `.env` so the local Grafana container is not started.
3. Restart the backend.

## Configuration and secrets

`KIBOOTIA_OBSERVABILITY_ENABLED` (the property `kibootia.observability.enabled`, default `true`). Where telemetry goes when it is on is described in [application configuration](../operations/configuration/application-configuration.md).

## Security

Nothing new. Structured logs to the console and the health endpoint on the management port are not affected.

## Testing and verification

The application starts and answers with the switch off; this is the `observability (switched off)` row of the CI job. That nothing is exported is not asserted: the switch only stops the exporter from being configured.

## Observability

Console JSON logs (with trace and span IDs when a trace exists) and `/actuator/health` on the management port remain.

## Removing or replacing it

To switch it on again, set the property to `true` and add `observability` back to `COMPOSE_PROFILES`.

## Related documents

[ADR 0030](../decisions/0030-otlp-telemetry-and-local-lgtm.md), [ADR 0024](../decisions/0024-package-modules-with-infrastructure-toggles.md), [remove notes](remove-notes.md), [disable files](disable-files.md).
