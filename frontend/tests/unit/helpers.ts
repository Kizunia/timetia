import { vi } from "vitest";

import type { Deps } from "@/lib/bff/deps";
import type { Config } from "@/lib/config";
import type { Oidc, Tokens } from "@/lib/oidc";
import { newSession, writeSession, type Session } from "@/lib/session";

// The session code checks expiry against the real clock, so the injected clock starts at the real time.
export const NOW_MS = Date.now();
export const NOW_S = Math.floor(NOW_MS / 1000);

export const config: Config = {
  publicBaseUrl: "http://localhost:3000",
  issuer: "http://localhost:8180/realms/timetia",
  internalIssuer: "http://keycloak:8080/realms/timetia",
  clientId: "timetia-web",
  clientSecret: "client-secret-for-tests",
  sessionSecret: "0123456789abcdef0123456789abcdef0123456789",
  apiBaseUrl: "http://api.internal:8080",
};

export const freshTokens: Tokens = {
  accessToken: "access-2",
  refreshToken: "refresh-2",
  idToken: "id-2",
  expiresIn: 300,
};

export function fakeOidc(overrides: Partial<Oidc> = {}): Oidc {
  return {
    createLogin: vi.fn(async () => ({
      url: "http://localhost:8180/realms/timetia/protocol/openid-connect/auth?state=s1",
      state: "s1",
      nonce: "n1",
      codeVerifier: "v1",
    })),
    exchangeCode: vi.fn(async () => freshTokens),
    refresh: vi.fn(async () => freshTokens),
    endSessionUrl: vi.fn((idToken) => `http://localhost:8180/realms/timetia/protocol/openid-connect/logout?hint=${idToken ?? "none"}`),
    ...overrides,
  };
}

export function makeDeps(overrides: Partial<Deps> = {}): Deps {
  return {
    config,
    oidc: fakeOidc(),
    now: () => NOW_MS,
    fetch: vi.fn(async () => new Response("{}", { status: 200 })) as unknown as typeof fetch,
    ...overrides,
  };
}

/** The Cookie header a browser would send after receiving these Set-Cookie values. */
export function cookieHeaderFrom(setCookies: string[]): string {
  const jar = new Map<string, string>();
  for (const setCookie of setCookies) {
    const [pair] = setCookie.split(";");
    const index = pair!.indexOf("=");
    const name = pair!.slice(0, index);
    const value = pair!.slice(index + 1);
    if (/Max-Age=0/i.test(setCookie) || !value) jar.delete(name);
    else jar.set(name, value);
  }
  return [...jar].map(([name, value]) => `${name}=${value}`).join("; ");
}

export async function sessionCookie(overrides: Partial<Session> = {}): Promise<string> {
  const session: Session = {
    ...newSession({ accessToken: "access-1", refreshToken: "refresh-1", idToken: "id-1", accessExpiresAt: NOW_S + 300 }, NOW_MS),
    ...overrides,
  };
  return cookieHeaderFrom(await writeSession(session, config.sessionSecret, false));
}

export function request(path: string, init: RequestInit & { cookie?: string; csrf?: boolean } = {}): Request {
  const { cookie, csrf, ...rest } = init;
  const headers = new Headers(rest.headers);
  if (cookie) headers.set("cookie", cookie);
  if (csrf) {
    headers.set("origin", config.publicBaseUrl);
    headers.set("x-timetia-request", "1");
  }
  return new Request(`${config.publicBaseUrl}${path}`, { ...rest, headers });
}

export function setCookiesOf(response: Response): string[] {
  return response.headers.getSetCookie();
}
