import { describe, expect, it } from "vitest";

import { CHUNK_SIZE, newSession, readSession, SESSION_COOKIE, seal, unseal, writeSession } from "@/lib/session";

import { cookieHeaderFrom, config, NOW_MS, NOW_S } from "./helpers";

const tokens = { accessToken: "a", refreshToken: "r", idToken: "i", accessExpiresAt: NOW_S + 300 };

describe("session cookie", () => {
  it("round-trips a session through an encrypted cookie", async () => {
    const session = newSession(tokens, Date.now());
    const header = cookieHeaderFrom(await writeSession(session, config.sessionSecret, false));
    expect(await readSession(header, config.sessionSecret)).toMatchObject({ accessToken: "a", refreshToken: "r" });
  });

  it("does not store tokens in readable form", async () => {
    const session = newSession({ ...tokens, accessToken: "visible-access-token" }, Date.now());
    const cookies = await writeSession(session, config.sessionSecret, false);
    expect(cookies.join(";")).not.toContain("visible-access-token");
  });

  it("is HttpOnly, SameSite=Lax and Secure on HTTPS", async () => {
    const session = newSession(tokens, Date.now());
    const [plain] = await writeSession(session, config.sessionSecret, false);
    const [secure] = await writeSession(session, config.sessionSecret, true);
    expect(plain).toMatch(/HttpOnly/);
    expect(plain).toMatch(/SameSite=Lax/);
    expect(plain).not.toMatch(/Secure/);
    expect(secure).toMatch(/Secure/);
  });

  it("splits a large session into chunks and reads it back", async () => {
    const big = newSession({ ...tokens, accessToken: "x".repeat(CHUNK_SIZE * 2) }, Date.now());
    const cookies = await writeSession(big, config.sessionSecret, false);
    expect(cookies.length).toBeGreaterThan(1);
    for (const cookie of cookies) expect(cookie.split(";")[0]!.length).toBeLessThan(4096);
    expect(await readSession(cookieHeaderFrom(cookies), config.sessionSecret)).toMatchObject({ accessToken: "x".repeat(CHUNK_SIZE * 2) });
  });

  it("expires chunks left over from an earlier, larger session", async () => {
    const big = newSession({ ...tokens, accessToken: "x".repeat(CHUNK_SIZE * 2) }, Date.now());
    const header = cookieHeaderFrom(await writeSession(big, config.sessionSecret, false));
    const small = await writeSession(newSession(tokens, Date.now()), config.sessionSecret, false, header);
    expect(small.some((cookie) => cookie.startsWith(`${SESSION_COOKIE}.1=`) && /Max-Age=0/.test(cookie))).toBe(true);
  });

  it("rejects a tampered cookie", async () => {
    const header = cookieHeaderFrom(await writeSession(newSession(tokens, Date.now()), config.sessionSecret, false));
    const tampered = header.replace(/.$/, (c) => (c === "A" ? "B" : "A"));
    expect(await readSession(tampered, config.sessionSecret)).toBeNull();
  });

  it("rejects a cookie sealed with another key", async () => {
    const header = cookieHeaderFrom(await writeSession(newSession(tokens, Date.now()), "another-secret-another-secret-another", false));
    expect(await readSession(header, config.sessionSecret)).toBeNull();
  });

  it("rejects a session past its absolute limit", async () => {
    const expired = { ...newSession(tokens, NOW_MS), sessionExpiresAt: Math.floor(Date.now() / 1000) - 10 };
    const sealed = await seal({ ...expired }, config.sessionSecret, 60);
    const header = `${SESSION_COOKIE}.0=${sealed}`;
    expect(await readSession(header, config.sessionSecret)).toBeNull();
  });

  it("returns null without a cookie", async () => {
    expect(await readSession(null, config.sessionSecret)).toBeNull();
    expect(await readSession("other=1", config.sessionSecret)).toBeNull();
  });

  it("does not accept an unencrypted token in place of the cookie", async () => {
    expect(await unseal("eyJhbGciOiJub25lIn0.e30.", config.sessionSecret)).toBeNull();
  });
});
