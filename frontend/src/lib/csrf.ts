import { problem } from "./problem";

/** Required on every request that changes state. The generated API client and the sign-out button both send it. */
export const CSRF_HEADER = "x-kibootia-request";
export const CSRF_HEADER_VALUE = "1";

const SAFE_METHODS = new Set(["GET", "HEAD"]);

/**
 * The CSRF rule of ADR 0042: every request other than GET and HEAD must carry an `Origin` that matches this server's
 * own origin and the custom header. A cross-site page cannot set the header without a CORS preflight, which this
 * server never grants, and it cannot forge `Origin`. Returns the rejection, or null when the request may proceed.
 */
export function checkCsrf(request: Request, publicBaseUrl: string): Response | null {
  if (SAFE_METHODS.has(request.method.toUpperCase())) return null;
  const origin = request.headers.get("origin");
  if (origin !== new URL(publicBaseUrl).origin) {
    return problem(403, "bff.csrf_origin", "The request origin is not allowed");
  }
  if (!request.headers.get(CSRF_HEADER)) {
    return problem(403, "bff.csrf_header", "The request is missing a required header");
  }
  return null;
}
