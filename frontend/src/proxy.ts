import { NextResponse, type NextRequest } from "next/server";

import { defaultDeps } from "@/lib/bff/deps";
import { currentSession } from "@/lib/bff/refresh";
import { parseCookies } from "@/lib/cookies";
import { SESSION_COOKIE } from "@/lib/session";

/**
 * Runs before page rendering. Server Components cannot set cookies, so a token that needs refreshing is refreshed
 * here, where the new session can be written to the response and handed on to the page that renders next (ADR 0026).
 * Pages that need a signed-in user send everyone else to the sign-in route.
 */
function needsSignIn(pathname: string): boolean {
  return pathname === "/me" || pathname === "/profile" || pathname === "/admin" || pathname.startsWith("/admin/");
}

/** Replaces the session chunks in a Cookie header with the ones just written, so the page sees the new session. */
function withNewSession(cookieHeader: string | null, setCookies: string[]): string {
  const cookies = parseCookies(cookieHeader);
  for (const name of [...cookies.keys()]) {
    if (name.startsWith(`${SESSION_COOKIE}.`)) cookies.delete(name);
  }
  for (const setCookie of setCookies) {
    const pair = setCookie.split(";")[0] ?? "";
    const index = pair.indexOf("=");
    const name = pair.slice(0, index);
    const value = pair.slice(index + 1);
    if (value && !/Max-Age=0/i.test(setCookie)) cookies.set(name, value);
  }
  return [...cookies].map(([name, value]) => `${name}=${value}`).join("; ");
}

export async function proxy(request: NextRequest) {
  const deps = defaultDeps();
  const cookieHeader = request.headers.get("cookie");
  const result = await currentSession(cookieHeader, deps);
  const signInRequired = needsSignIn(request.nextUrl.pathname);

  if (result.status !== "ok") {
    if (!signInRequired) {
      const response = NextResponse.next();
      for (const cookie of result.setCookies) response.headers.append("Set-Cookie", cookie);
      return response;
    }
    const login = new URL("/auth/login", deps.config.publicBaseUrl);
    login.searchParams.set("returnTo", request.nextUrl.pathname);
    const response = NextResponse.redirect(login);
    for (const cookie of result.setCookies) response.headers.append("Set-Cookie", cookie);
    return response;
  }

  if (result.setCookies.length === 0) return NextResponse.next();

  const headers = new Headers(request.headers);
  headers.set("cookie", withNewSession(cookieHeader, result.setCookies));
  const response = NextResponse.next({ request: { headers } });
  for (const cookie of result.setCookies) response.headers.append("Set-Cookie", cookie);
  return response;
}

export const config = {
  matcher: ["/", "/me", "/profile", "/admin", "/admin/:path*"],
};
