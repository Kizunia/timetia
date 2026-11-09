import { describe, expect, it, vi } from "vitest";

import { callback, login, logout, safeReturnTo } from "@/lib/bff/auth";
import { LOGIN_COOKIE, SESSION_COOKIE, readSession, seal } from "@/lib/session";

import { cookieHeaderFrom, config, fakeOidc, freshTokens, makeDeps, NOW_S, request, sessionCookie, setCookiesOf } from "./helpers";

const loginState = { state: "s1", nonce: "n1", codeVerifier: "v1", returnTo: "/me" };

async function loginCookie(state: object = loginState): Promise<string> {
  return `${LOGIN_COOKIE}=${await seal({ ...state }, config.sessionSecret, 600)}`;
}

describe("GET /auth/login", () => {
  it("redirects to Keycloak and keeps state, nonce and verifier in a short-lived HttpOnly cookie", async () => {
    const deps = makeDeps();
    const response = await login(request("/auth/login?returnTo=/me"), deps);
    expect(response.status).toBe(302);
    expect(response.headers.get("location")).toContain("/protocol/openid-connect/auth");
    expect(deps.oidc.createLogin).toHaveBeenCalledWith("http://localhost:3000/auth/callback");
    const [cookie] = setCookiesOf(response);
    expect(cookie).toMatch(new RegExp(`^${LOGIN_COOKIE}=`));
    expect(cookie).toMatch(/HttpOnly/);
    expect(cookie).toMatch(/Max-Age=600/);
  });

  it.each([
    ["https://evil.example", "/"],
    ["//evil.example", "/"],
    ["/\\evil.example", "/"],
    ["javascript:alert(1)", "/"],
    ["/me", "/me"],
    ["/me?tab=1", "/me?tab=1"],
    [null, "/"],
  ])("only accepts a same-site return path (%s)", (value, expected) => {
    expect(safeReturnTo(value)).toBe(expected);
  });
});

describe("GET /auth/callback", () => {
  it("exchanges the code, stores the session and returns the user to where they were going", async () => {
    const deps = makeDeps();
    const response = await callback(request("/auth/callback?code=abc&state=s1", { cookie: await loginCookie() }), deps);

    expect(response.status).toBe(302);
    expect(response.headers.get("location")).toBe("http://localhost:3000/me");
    expect(deps.oidc.exchangeCode).toHaveBeenCalledWith(expect.any(URL), { state: "s1", nonce: "n1", codeVerifier: "v1" });

    const cookies = setCookiesOf(response);
    const session = await readSession(cookieHeaderFrom(cookies), config.sessionSecret);
    expect(session).toMatchObject({ accessToken: freshTokens.accessToken, refreshToken: freshTokens.refreshToken });
    expect(session!.accessExpiresAt).toBe(NOW_S + freshTokens.expiresIn);
    expect(cookies.some((cookie) => cookie.startsWith(`${LOGIN_COOKIE}=;`) && /Max-Age=0/.test(cookie))).toBe(true);
    expect(cookies.join("\n")).not.toContain(freshTokens.accessToken);
  });

  it("gives the exchange the public callback URL even when the request arrived on an internal address", async () => {
    const deps = makeDeps();
    const internal = new Request("http://frontend:3000/auth/callback?code=abc&state=s1", { headers: { cookie: await loginCookie() } });
    await callback(internal, deps);
    const url = vi.mocked(deps.oidc.exchangeCode).mock.calls[0]![0];
    expect(url.origin).toBe("http://localhost:3000");
    expect(url.search).toBe("?code=abc&state=s1");
  });

  it("rejects the callback when the state, nonce or verifier check fails, and sets no session", async () => {
    const deps = makeDeps({ oidc: fakeOidc({ exchangeCode: vi.fn(async () => { throw new Error("state mismatch"); }) }) });
    const response = await callback(request("/auth/callback?code=abc&state=wrong", { cookie: await loginCookie() }), deps);
    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("bff.login_failed");
    expect(setCookiesOf(response).some((cookie) => cookie.startsWith(SESSION_COOKIE))).toBe(false);
  });

  it("rejects a callback that did not start with a sign-in here", async () => {
    const response = await callback(request("/auth/callback?code=abc&state=s1"), makeDeps());
    expect(response.status).toBe(400);
    expect((await response.json()).code).toBe("bff.login_expired");
  });

  it("rejects a callback with a tampered login cookie", async () => {
    const forged = `${LOGIN_COOKIE}=${await seal({ ...loginState }, "some-other-secret-some-other-secret-xx", 600)}`;
    const response = await callback(request("/auth/callback?code=abc&state=s1", { cookie: forged }), makeDeps());
    expect(response.status).toBe(400);
  });

  it("never sends an off-site return path to the browser", async () => {
    const response = await callback(
      request("/auth/callback?code=abc&state=s1", { cookie: await loginCookie({ ...loginState, returnTo: "//evil.example" }) }),
      makeDeps(),
    );
    expect(response.headers.get("location")).toBe("http://localhost:3000/");
  });
});

describe("POST /auth/logout", () => {
  it("clears the session and returns the Keycloak sign-out address with the ID token hint", async () => {
    const deps = makeDeps();
    const response = await logout(request("/auth/logout", { method: "POST", csrf: true, cookie: await sessionCookie() }), deps);
    expect(response.status).toBe(200);
    expect((await response.json()).redirectTo).toContain("hint=id-1");
    const cookies = setCookiesOf(response);
    expect(cookies.length).toBeGreaterThan(0);
    expect(cookies.every((cookie) => /Max-Age=0/.test(cookie))).toBe(true);
  });

  it("still signs out when there is no session", async () => {
    const response = await logout(request("/auth/logout", { method: "POST", csrf: true }), makeDeps());
    expect(response.status).toBe(200);
    expect((await response.json()).redirectTo).toContain("hint=none");
  });

  it("is protected by the CSRF rule", async () => {
    const deps = makeDeps();
    const noOrigin = await logout(request("/auth/logout", { method: "POST", cookie: await sessionCookie() }), deps);
    expect(noOrigin.status).toBe(403);
    expect(setCookiesOf(noOrigin)).toEqual([]);
    const foreign = await logout(
      request("/auth/logout", { method: "POST", cookie: await sessionCookie(), headers: { origin: "https://evil.example", "x-kibootia-request": "1" } }),
      deps,
    );
    expect(foreign.status).toBe(403);
  });
});
