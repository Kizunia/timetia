import { describe, expect, it, vi } from "vitest";

import { refreshNow } from "@/lib/bff/auth";
import { currentSession } from "@/lib/bff/refresh";
import { CSRF_HEADER } from "@/lib/csrf";
import { newSession, writeSession } from "@/lib/session";

import { config, cookieHeaderFrom, fakeOidc, freshTokens, makeDeps, NOW_MS, NOW_S } from "./helpers";

/** A session whose access token has plenty of time left, so only a forced refresh would renew it. */
async function freshCookie(): Promise<string> {
  const session = newSession(
    { accessToken: "access-1", refreshToken: "refresh-1", idToken: "id-1", accessExpiresAt: NOW_S + 280 },
    NOW_MS,
  );
  return cookieHeaderFrom(await writeSession(session, config.sessionSecret, false));
}

function post(cookie: string | null, headers: Record<string, string> = {}): Request {
  return new Request("http://localhost:3000/auth/refresh", {
    method: "POST",
    headers: {
      origin: "http://localhost:3000",
      [CSRF_HEADER]: "1",
      ...(cookie ? { cookie } : {}),
      ...headers,
    },
  });
}

describe("refreshing the session on demand (the 'I have verified' button)", () => {
  it("does not refresh a token that has time left when nobody asks", async () => {
    const oidc = fakeOidc();

    const result = await currentSession(await freshCookie(), makeDeps({ oidc }));

    expect(result.status).toBe("ok");
    expect(result.setCookies).toEqual([]);
    expect(oidc.refresh).not.toHaveBeenCalled();
  });

  it("refreshes a token that has time left when asked to, and stores the new tokens", async () => {
    const oidc = fakeOidc();

    const result = await currentSession(await freshCookie(), makeDeps({ oidc }), { force: true });

    expect(oidc.refresh).toHaveBeenCalledWith("refresh-1");
    expect(result.status).toBe("ok");
    expect(result.setCookies.length).toBeGreaterThan(0);
    if (result.status === "ok") expect(result.session.accessToken).toBe(freshTokens.accessToken);
  });

  it("answers 204 with the new session cookies", async () => {
    const oidc = fakeOidc();

    const response = await refreshNow(post(await freshCookie()), makeDeps({ oidc }));

    expect(response.status).toBe(204);
    expect(oidc.refresh).toHaveBeenCalledOnce();
    expect(response.headers.getSetCookie().some((cookie) => cookie.startsWith("timetia_session.0="))).toBe(true);
    expect(response.headers.get("Cache-Control")).toBe("no-store");
  });

  it("answers 401 when there is no session, and refreshes nothing", async () => {
    const oidc = fakeOidc();

    const response = await refreshNow(post(null), makeDeps({ oidc }));

    expect(response.status).toBe(401);
    expect((await response.json()).code).toBe("bff.unauthenticated");
    expect(oidc.refresh).not.toHaveBeenCalled();
  });

  it("answers 401 and clears the session when Keycloak refuses the refresh token", async () => {
    const oidc = fakeOidc({ refresh: vi.fn(async () => Promise.reject(new Error("invalid_grant"))) });

    const response = await refreshNow(post(await freshCookie()), makeDeps({ oidc }));

    expect(response.status).toBe(401);
    expect(response.headers.getSetCookie().every((cookie) => /Max-Age=0/i.test(cookie))).toBe(true);
  });

  it("is a state-changing request, so the CSRF rule applies: a foreign origin or a missing header is 403", async () => {
    const oidc = fakeOidc();
    const cookie = await freshCookie();

    const foreign = await refreshNow(post(cookie, { origin: "http://evil.example" }), makeDeps({ oidc }));
    const bare = await refreshNow(
      new Request("http://localhost:3000/auth/refresh", { method: "POST", headers: { origin: "http://localhost:3000", cookie } }),
      makeDeps({ oidc }),
    );

    expect(foreign.status).toBe(403);
    expect(bare.status).toBe(403);
    expect(oidc.refresh).not.toHaveBeenCalled();
  });
});
