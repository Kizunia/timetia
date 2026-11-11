import "server-only";

/**
 * Server-side settings of the Next.js server (ADR 0026, ADR 0040). Read when first used, so a missing required value
 * fails the first request with a clear message instead of failing in the middle of a sign-in.
 *
 * Keycloak has two addresses: the public one that browsers use and that appears as the token issuer, and an
 * internal one for the server's own calls (the same host when both run on the developer's machine, `keycloak:8080`
 * inside the `apps` Compose profile).
 */
export interface Config {
  /** Public origin of this server, without a trailing slash. */
  publicBaseUrl: string;
  /** Public realm URL; the exact `iss` value of tokens. Browsers are sent here. */
  issuer: string;
  /** Realm URL the server itself uses for token and key requests. */
  internalIssuer: string;
  clientId: string;
  clientSecret: string;
  sessionSecret: string;
  /** Fixed upstream of the `/api` proxy: the Spring API. */
  apiBaseUrl: string;
}

export class MissingSettingError extends Error {
  constructor(readonly setting: string, hint: string) {
    super(`Missing required setting ${setting}. ${hint}`);
    this.name = "MissingSettingError";
  }
}

type Env = Record<string, string | undefined>;

function required(env: Env, name: string): string {
  const value = env[name];
  if (!value) {
    throw new MissingSettingError(name, "Run 'pnpm bootstrap' to create .env, then start the server with 'pnpm dev'.");
  }
  return value;
}

function stripTrailingSlash(value: string): string {
  return value.replace(/\/+$/, "");
}

export function loadConfig(env: Env = process.env): Config {
  const issuer = stripTrailingSlash(env.KEYCLOAK_ISSUER || "http://localhost:8180/realms/timetia");
  const sessionSecret = required(env, "SESSION_SECRET");
  if (sessionSecret.length < 32) {
    throw new MissingSettingError("SESSION_SECRET", "It must be at least 32 characters; 'pnpm bootstrap' generates one.");
  }
  return {
    publicBaseUrl: stripTrailingSlash(env.PUBLIC_BASE_URL || "http://localhost:3470"),
    issuer,
    internalIssuer: stripTrailingSlash(env.KEYCLOAK_INTERNAL_ISSUER || issuer),
    clientId: env.KEYCLOAK_WEB_CLIENT_ID || "timetia-web",
    clientSecret: required(env, "KEYCLOAK_WEB_CLIENT_SECRET"),
    sessionSecret,
    apiBaseUrl: stripTrailingSlash(env.API_BASE_URL || "http://localhost:8080"),
  };
}

let cached: Config | undefined;

export function config(): Config {
  cached ??= loadConfig();
  return cached;
}
