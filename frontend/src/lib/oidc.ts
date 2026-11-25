import * as client from "openid-client";

import type { Config } from "./config";

/** What the BFF needs from the identity provider. A narrow interface so the handlers can be tested without Keycloak. */
export interface Tokens {
  accessToken: string;
  refreshToken: string;
  idToken: string;
  /** Seconds until the access token expires. */
  expiresIn: number;
}

export interface LoginRequest {
  url: string;
  state: string;
  nonce: string;
  codeVerifier: string;
}

export interface Oidc {
  createLogin(redirectUri: string): Promise<LoginRequest>;
  exchangeCode(currentUrl: URL, expected: { state: string; nonce: string; codeVerifier: string }): Promise<Tokens>;
  refresh(refreshToken: string): Promise<Tokens>;
  endSessionUrl(idToken: string | undefined): string;
}

/**
 * Keycloak's endpoints are written out instead of discovered: browser-facing ones use the public address, server-to-
 * server ones the internal address, and the issuer stays the public one that tokens carry (ADR 0040, OQ-45). Discovery
 * would reject that split, because the document's issuer differs from the URL it was fetched from.
 */
export function createOidc(config: Config): Oidc {
  const configuration = new client.Configuration(
    {
      issuer: config.issuer,
      authorization_endpoint: `${config.issuer}/protocol/openid-connect/auth`,
      token_endpoint: `${config.internalIssuer}/protocol/openid-connect/token`,
      jwks_uri: `${config.internalIssuer}/protocol/openid-connect/certs`,
      end_session_endpoint: `${config.issuer}/protocol/openid-connect/logout`,
      id_token_signing_alg_values_supported: ["RS256"],
    },
    config.clientId,
    { client_secret: config.clientSecret },
  );
  if (config.issuer.startsWith("http://") || config.internalIssuer.startsWith("http://")) {
    // Local development runs Keycloak over plain HTTP. Production uses HTTPS and never reaches this branch.
    client.allowInsecureRequests(configuration);
  }

  const toTokens = (response: client.TokenEndpointResponse): Tokens => {
    if (!response.access_token || !response.refresh_token || !response.id_token) {
      throw new Error("The token response is missing the access, refresh or ID token");
    }
    return {
      accessToken: response.access_token,
      refreshToken: response.refresh_token,
      idToken: response.id_token,
      expiresIn: response.expires_in ?? 300,
    };
  };

  return {
    async createLogin(redirectUri) {
      const state = client.randomState();
      const nonce = client.randomNonce();
      const codeVerifier = client.randomPKCECodeVerifier();
      const url = client.buildAuthorizationUrl(configuration, {
        redirect_uri: redirectUri,
        scope: "openid profile email",
        state,
        nonce,
        code_challenge: await client.calculatePKCECodeChallenge(codeVerifier),
        code_challenge_method: "S256",
      });
      return { url: url.href, state, nonce, codeVerifier };
    },

    async exchangeCode(currentUrl, expected) {
      const response = await client.authorizationCodeGrant(configuration, currentUrl, {
        pkceCodeVerifier: expected.codeVerifier,
        expectedState: expected.state,
        expectedNonce: expected.nonce,
        idTokenExpected: true,
      });
      return toTokens(response);
    },

    async refresh(refreshToken) {
      return toTokens(await client.refreshTokenGrant(configuration, refreshToken));
    },

    endSessionUrl(idToken) {
      const parameters: Record<string, string> = {
        post_logout_redirect_uri: config.publicBaseUrl,
        client_id: config.clientId,
      };
      if (idToken) parameters.id_token_hint = idToken;
      return client.buildEndSessionUrl(configuration, parameters).href;
    },
  };
}
