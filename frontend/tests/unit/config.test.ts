import { describe, expect, it } from "vitest";

import { loadConfig, MissingSettingError } from "@/lib/config";

const valid = { SESSION_SECRET: "x".repeat(40), KEYCLOAK_WEB_CLIENT_SECRET: "client-secret" };

describe("frontend settings", () => {
  it("uses the local defaults when only the secrets are set", () => {
    const config = loadConfig(valid);
    expect(config).toMatchObject({
      publicBaseUrl: "http://localhost:3470",
      issuer: "http://localhost:8180/realms/kibootia",
      internalIssuer: "http://localhost:8180/realms/kibootia",
      apiBaseUrl: "http://localhost:8080",
      clientId: "kibootia-web",
    });
  });

  it("keeps the public and internal Keycloak addresses apart", () => {
    const config = loadConfig({ ...valid, KEYCLOAK_INTERNAL_ISSUER: "http://keycloak:8080/realms/kibootia/" });
    expect(config.issuer).toBe("http://localhost:8180/realms/kibootia");
    expect(config.internalIssuer).toBe("http://keycloak:8080/realms/kibootia");
  });

  it("names the missing setting without printing any value", () => {
    expect(() => loadConfig({ KEYCLOAK_WEB_CLIENT_SECRET: "secret-value" })).toThrow(/SESSION_SECRET/);
    try {
      loadConfig({ SESSION_SECRET: "x".repeat(40) });
      expect.unreachable();
    } catch (error) {
      expect(error).toBeInstanceOf(MissingSettingError);
      expect((error as Error).message).toContain("KEYCLOAK_WEB_CLIENT_SECRET");
      expect((error as Error).message).not.toContain("xxxx");
    }
  });

  it("refuses a session secret that is too short to be safe", () => {
    expect(() => loadConfig({ ...valid, SESSION_SECRET: "short" })).toThrow(/at least 32/);
  });
});
