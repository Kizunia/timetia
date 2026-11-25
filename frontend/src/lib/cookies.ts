/** Cookie parsing and serialization on plain headers, so the same code serves route handlers, the proxy and tests. */

export interface CookieOptions {
  maxAge?: number;
  /** SameSite=Lax is required for the session: the browser returns from Keycloak by a top-level navigation. */
  sameSite?: "Lax" | "Strict";
  secure?: boolean;
}

export function parseCookies(header: string | null | undefined): Map<string, string> {
  const cookies = new Map<string, string>();
  if (!header) return cookies;
  for (const part of header.split(";")) {
    const index = part.indexOf("=");
    if (index < 0) continue;
    const name = part.slice(0, index).trim();
    const value = part.slice(index + 1).trim();
    if (name && !cookies.has(name)) cookies.set(name, value);
  }
  return cookies;
}

/** Values are base64url or digits, so they need no escaping. */
export function serializeCookie(name: string, value: string, options: CookieOptions = {}): string {
  const parts = [`${name}=${value}`, "Path=/", "HttpOnly", `SameSite=${options.sameSite ?? "Lax"}`];
  if (options.secure) parts.push("Secure");
  if (options.maxAge !== undefined) parts.push(`Max-Age=${options.maxAge}`);
  return parts.join("; ");
}

export function expireCookie(name: string, secure: boolean): string {
  return serializeCookie(name, "", { maxAge: 0, secure });
}
