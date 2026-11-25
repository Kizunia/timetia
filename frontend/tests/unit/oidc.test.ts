import { describe, expect, it } from "vitest";

import { createOidc } from "@/lib/oidc";

import { config } from "./helpers";

describe("OIDC client setup", () => {
  const oidc = createOidc(config);

  it("sends the browser to the public Keycloak address with PKCE, state and nonce", async () => {
    const login = await oidc.createLogin("http://localhost:3000/auth/callback");
    const url = new URL(login.url);
    expect(url.origin).toBe("http://localhost:8180");
    expect(url.pathname).toBe("/realms/timetia/protocol/openid-connect/auth");
    expect(url.searchParams.get("client_id")).toBe("timetia-web");
    expect(url.searchParams.get("redirect_uri")).toBe("http://localhost:3000/auth/callback");
    expect(url.searchParams.get("response_type")).toBe("code");
    expect(url.searchParams.get("code_challenge_method")).toBe("S256");
    expect(url.searchParams.get("code_challenge")).toBeTruthy();
    expect(url.searchParams.get("state")).toBe(login.state);
    expect(url.searchParams.get("nonce")).toBe(login.nonce);
    expect(url.searchParams.get("scope")).toBe("openid profile email");
  });

  it("never puts the code verifier or the client secret in the authorization URL", async () => {
    const login = await oidc.createLogin("http://localhost:3000/auth/callback");
    expect(login.url).not.toContain(login.codeVerifier);
    expect(login.url).not.toContain(config.clientSecret);
  });

  it("uses a different state, nonce and verifier each time", async () => {
    const [a, b] = await Promise.all([oidc.createLogin("http://localhost:3000/auth/callback"), oidc.createLogin("http://localhost:3000/auth/callback")]);
    expect(a.state).not.toBe(b.state);
    expect(a.nonce).not.toBe(b.nonce);
    expect(a.codeVerifier).not.toBe(b.codeVerifier);
  });

  it("ends the Keycloak session at the public address and returns to this site", () => {
    const url = new URL(oidc.endSessionUrl("the-id-token"));
    expect(url.origin).toBe("http://localhost:8180");
    expect(url.pathname).toBe("/realms/timetia/protocol/openid-connect/logout");
    expect(url.searchParams.get("id_token_hint")).toBe("the-id-token");
    expect(url.searchParams.get("post_logout_redirect_uri")).toBe("http://localhost:3000");
  });
});
