import { config as loadedConfig, type Config } from "../config";
import { createOidc, type Oidc } from "../oidc";

/** Everything the BFF handlers depend on. Tests pass fakes; the Next routes use {@link defaultDeps}. */
export interface Deps {
  config: Config;
  oidc: Oidc;
  /** Epoch milliseconds. */
  now: () => number;
  fetch: typeof fetch;
}

let cached: Deps | undefined;

export function defaultDeps(): Deps {
  if (!cached) {
    const config = loadedConfig();
    cached = { config, oidc: createOidc(config), now: () => Date.now(), fetch: (...args) => fetch(...args) };
  }
  return cached;
}

export function isSecure(config: Config): boolean {
  return config.publicBaseUrl.startsWith("https://");
}
