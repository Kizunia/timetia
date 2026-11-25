import { registerOTel } from "@vercel/otel";

/**
 * OpenTelemetry for the Next.js server (ADR 0030): traces go over OTLP to the endpoint in OTEL_EXPORTER_OTLP_ENDPOINT,
 * and the W3C trace context is propagated on outgoing calls, so a request is one trace across Next.js and Spring.
 * Switch it off with KIBOOTIA_OBSERVABILITY_ENABLED=false.
 */
export function register() {
  if (process.env.KIBOOTIA_OBSERVABILITY_ENABLED === "false") return;
  registerOTel({ serviceName: "kibootia-frontend" });
}
