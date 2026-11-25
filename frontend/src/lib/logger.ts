import { trace } from "@opentelemetry/api";
import pino from "pino";

/**
 * Structured JSON logs on stdout with the trace and span ID of the active span, so a log line can be matched to its
 * trace (observability baseline). Callers must never pass tokens, cookies or secrets.
 */
export const logger = pino({
  level: process.env.LOG_LEVEL ?? "info",
  base: { service: "kibootia-frontend" },
  timestamp: pino.stdTimeFunctions.isoTime,
  formatters: { level: (label) => ({ level: label }) },
  messageKey: "message",
  mixin() {
    const context = trace.getActiveSpan()?.spanContext();
    return context ? { trace_id: context.traceId, span_id: context.spanId } : {};
  },
});
