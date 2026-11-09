import { context, propagation, type Context } from "@opentelemetry/api";

import { checkCsrf, CSRF_HEADER } from "../csrf";
import { logger } from "../logger";
import { problem } from "../problem";
import type { Deps } from "./deps";
import { currentSession } from "./refresh";

/**
 * The generic `/api/*` proxy (ADR 0042). The upstream is fixed to the Spring API, so the browser can never use this
 * server to reach another host. It attaches the access token, enforces the CSRF rule, streams bodies in both
 * directions and passes W3C trace context. The browser never sees a token, and cookies never reach Spring.
 */

const HOP_BY_HOP = new Set([
  "connection",
  "keep-alive",
  "proxy-authenticate",
  "proxy-authorization",
  "proxy-connection",
  "te",
  "trailer",
  "transfer-encoding",
  "upgrade",
]);

/** Request headers that are never forwarded: credentials, the browser's origin data and anything proxies would set. */
const DROPPED_REQUEST_HEADERS = new Set([
  ...HOP_BY_HOP,
  "host",
  "cookie",
  "authorization",
  "origin",
  "referer",
  "content-length",
  CSRF_HEADER,
  "forwarded",
  "x-forwarded-for",
  "x-forwarded-host",
  "x-forwarded-proto",
  "x-forwarded-port",
  // Trace context comes from this server's own span, never from the browser (it would let a caller forge trace links).
  "traceparent",
  "tracestate",
  "baggage",
]);

/** The body is already decoded by `fetch`, so the upstream's encoding and length no longer describe it. */
const DROPPED_RESPONSE_HEADERS = new Set([...HOP_BY_HOP, "set-cookie", "content-encoding", "content-length"]);

const ALLOWED_METHODS = new Set(["GET", "HEAD", "POST", "PUT", "PATCH", "DELETE"]);
const NO_BODY_STATUSES = new Set([101, 204, 205, 304]);

/** Rejects anything that could climb out of /api: dot segments, encoded slashes and control characters. */
export function isSafeApiPath(pathname: string): boolean {
  if (!pathname.startsWith("/api/") || pathname.length === "/api/".length) return false;
  for (const raw of pathname.split("/").slice(1)) {
    let segment: string;
    try {
      segment = decodeURIComponent(raw);
    } catch {
      return false;
    }
    if (segment === "." || segment === ".." || segment.includes("/") || segment.includes("\\") || /[\u0000-\u001f]/.test(segment)) {
      return false;
    }
  }
  return true;
}

export async function proxyApi(request: Request, deps: Deps, traceContext: Context = context.active()): Promise<Response> {
  const { config } = deps;
  if (!ALLOWED_METHODS.has(request.method.toUpperCase())) {
    return problem(405, "bff.method_not_allowed", "The method is not allowed", { Allow: [...ALLOWED_METHODS].join(", ") });
  }

  const rejected = checkCsrf(request, config.publicBaseUrl);
  if (rejected) return rejected;

  const incoming = new URL(request.url);
  if (!isSafeApiPath(incoming.pathname)) {
    return problem(400, "bff.invalid_path", "The request path is not valid");
  }

  const result = await currentSession(request.headers.get("cookie"), deps);
  if (result.status !== "ok") {
    const headers = new Headers();
    for (const cookie of result.setCookies) headers.append("Set-Cookie", cookie);
    return problem(401, "bff.unauthenticated", "Sign in to continue", headers);
  }

  const target = new URL(`${config.apiBaseUrl}${incoming.pathname}${incoming.search}`);
  if (target.origin !== new URL(config.apiBaseUrl).origin) {
    return problem(400, "bff.invalid_path", "The request path is not valid");
  }

  const headers = new Headers();
  for (const [name, value] of request.headers) {
    if (!DROPPED_REQUEST_HEADERS.has(name.toLowerCase())) headers.set(name, value);
  }
  headers.set("Authorization", `Bearer ${result.session.accessToken}`);
  propagation.inject(traceContext, headers, {
    set: (carrier, key, value) => {
      carrier.set(key, value);
    },
  });

  const hasBody = request.method !== "GET" && request.method !== "HEAD";
  let upstream: Response;
  try {
    upstream = await deps.fetch(target, {
      method: request.method,
      headers,
      body: hasBody ? request.body : undefined,
      // Streams the request body instead of buffering it.
      duplex: "half",
      redirect: "manual",
      signal: request.signal,
    } as RequestInit & { duplex: "half" });
  } catch (error) {
    logger.error({ reason: error instanceof Error ? error.name : "unknown", path: incoming.pathname }, "API upstream unreachable");
    return problem(502, "bff.upstream_unavailable", "The API is not reachable", cookieHeaders(result.setCookies));
  }

  const responseHeaders = new Headers();
  for (const [name, value] of upstream.headers) {
    if (!DROPPED_RESPONSE_HEADERS.has(name.toLowerCase())) responseHeaders.set(name, value);
  }
  // Only the refreshed session may set cookies; whatever the upstream asks for is ignored.
  for (const cookie of result.setCookies) responseHeaders.append("Set-Cookie", cookie);

  logger.debug({ method: request.method, path: incoming.pathname, status: upstream.status }, "proxied API request");
  return new Response(NO_BODY_STATUSES.has(upstream.status) ? null : upstream.body, {
    status: upstream.status,
    statusText: upstream.statusText,
    headers: responseHeaders,
  });
}

function cookieHeaders(setCookies: string[]): Headers {
  const headers = new Headers();
  for (const cookie of setCookies) headers.append("Set-Cookie", cookie);
  return headers;
}
